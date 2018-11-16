package net.shrimpworks.unreal.archive.www;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.rjeschke.txtmark.Processor;

import net.shrimpworks.unreal.archive.docs.Document;
import net.shrimpworks.unreal.archive.docs.DocumentManager;

import static net.shrimpworks.unreal.archive.www.Templates.slug;

public class Documents {

	private static final String SECTION = "Articles";

	private final DocumentManager documents;
	private final Path root;
	private final Path staticRoot;

	private final Map<String, DocumentGroup> groups;

	public Documents(DocumentManager documents, Path root, Path staticRoot) {
		this.documents = documents;
		this.root = root.resolve("documents");
		this.staticRoot = staticRoot;

		this.groups = new HashMap<>();

		documents.all().stream()
				 .filter(d -> d.published)
				 .forEach(d -> {
					 DocumentGroup group = groups.computeIfAbsent(d.game, g -> new DocumentGroup(null, g));
					 group.add(d);
				 });
	}

	/*
	{
		"name": "General",
		"groups": [
			{
				"name": "Stuff"
				"documents": [
					{...}
				]
			}
		],
		"documents": [
			{...},
			{...}
		]
	}
	*/

	/**
	 * Generate one or more HTML pages of output.
	 *
	 * @return number of individual pages created
	 */
	public int generate() {
		int count = 0;
		try {
			// create the root landing page, for reasons
			DocumentGroup rootGroup = new DocumentGroup(null, "");
			rootGroup.groups.putAll(groups);
			count += generateGroup(rootGroup);

			for (DocumentGroup group : groups.values()) {
				count += generateGroup(group);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to render page", e);
		}

		return count;
	}

	private int generateGroup(DocumentGroup group) throws IOException {
		int count = 0;

		Templates.template("docs/group.ftl")
				 .put("static", root.resolve(group.path).relativize(staticRoot))
				 .put("title", String.join(" / ", SECTION, String.join(" / ", group.pPath.split("/"))))
				 .put("group", group)
				 .put("siteRoot", root.resolve(group.path).relativize(root))
				 .write(root.resolve(group.path).resolve("index.html"));

		count++;

		for (DocumentGroup g : group.groups.values()) {
			count += generateGroup(g);
		}

		for (Document d : group.documents) {
			count += generateDocument(group, d);
		}

		return count;
	}

	private int generateDocument(DocumentGroup group, Document doc) throws IOException {

		try (ReadableByteChannel docChan = documents.document(doc)) {

			String slug = slug(doc.title);
			Path path = root.resolve(String.join("/", group.path, slug));

			String content = Processor.process(Channels.newInputStream(docChan));

			Templates.template("docs/document.ftl")
					 .put("static", path.relativize(staticRoot))
					 .put("title", String.join(" / ", SECTION, String.join(" / ", group.pPath.split("/")), doc.title))
					 .put("group", group)
					 .put("document", doc)
					 .put("content", content)
					 .put("siteRoot", path.relativize(root))
					 .write(path.resolve("index.html"));
		}

		return 1;
	}

	public class DocumentGroup {

		private final String pPath;

		public final String name;
		public final String slug;
		public final String path;
		public final DocumentGroup parent;

		public final TreeMap<String, DocumentGroup> groups = new TreeMap<>();
		public final List<Document> documents = new ArrayList<>();

		public int docs;

		public DocumentGroup(DocumentGroup parent, String name) {
			this.pPath = parent != null ? parent.pPath.isEmpty() ? name : String.join("/", parent.pPath, name) : "";

			this.parent = parent;

			this.name = name;
			this.slug = slug(name);
			this.path = parent != null ? String.join("/", parent.path, slug) : slug;
			this.docs = 0;
		}

		public void add(Document d) {
			if (d.path.equals(pPath)) {
				documents.add(d);
			} else {
				String[] next = (pPath.isEmpty() ? d.path : d.path.replaceFirst(pPath + "/", "")).split("/");
				String nextName = (next.length > 0 && !next[0].isEmpty()) ? next[0] : "";

				DocumentGroup group = groups.computeIfAbsent(nextName, g -> new DocumentGroup(this, g));
				group.add(d);
			}
			this.docs++;
		}
	}

}