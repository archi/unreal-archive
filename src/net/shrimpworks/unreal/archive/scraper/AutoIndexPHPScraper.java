package net.shrimpworks.unreal.archive.scraper;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import net.shrimpworks.unreal.archive.CLI;
import net.shrimpworks.unreal.archive.YAML;

public class AutoIndexPHPScraper {

	private static final Pattern DIR_MATCH = Pattern.compile(".+?dir=([^&]+).+?");

	public static void index(CLI cli) throws IOException {
		if (cli.commands().length < 3) throw new IllegalArgumentException("A root URL is required!");

		final Connection connection = Jsoup.connect(cli.commands()[2]);
		connection.timeout(60000);

		final Set<String> visited = new HashSet<>();

		final List<FoundUrl> foundList = new ArrayList<>();

		final long slowdown = Long.valueOf(cli.option("slowdown", "2500"));

		index(connection, cli.commands()[2], cli.option("style-prefix", "default"), new Consumer<Found>() {
			@Override
			public void accept(Found found) {
				visited.add(found.url);

				foundList.addAll(found.files());

				for (FoundUrl dir : found.dirs()) {
					if (!visited.contains(dir.url)) {
						try {
							if (slowdown > 0) Thread.sleep(slowdown);
							index(connection, dir.url, cli.option("style-prefix", "default"), this);
						} catch (InterruptedException | IOException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}
		});

		System.out.println(YAML.toString(foundList));
	}

	private static void index(Connection connection, String url, String style, Consumer<Found> completed) throws IOException {
		System.err.printf("Indexing URL: %s%n", url);

		Document doc = connection.url(url).get();

		Elements links = doc.select(String.format("td.%s_td a.%s_a", style, style));

		List<FoundUrl> collected = links.stream()
										.filter(e -> !e.text().equalsIgnoreCase("parent directory"))
										.filter(e -> !e.attr("href").contains("md5"))
										.map(e -> {
											Matcher m = DIR_MATCH.matcher(e.absUrl("href"));
											String dir = "";
											if (m.matches()) {
												dir = m.group(1);
											}

											return new FoundUrl(e.text(), dir, e.absUrl("href"), url);
										})
										.sorted(Comparator.comparing(o -> o.name))
										.collect(Collectors.toList());

		completed.accept(new Found(url, collected));
	}

	private static class Found {

		private final String url;
		private final List<FoundUrl> found;

		public Found(String url, List<FoundUrl> found) {
			this.url = url;
			this.found = found;
		}

		public List<FoundUrl> files() {
			return found.stream().filter(f -> !f.dir()).collect(Collectors.toList());
		}

		public List<FoundUrl> dirs() {
			return found.stream().filter(FoundUrl::dir).collect(Collectors.toList());
		}

		@Override
		public String toString() {
			return String.format("Found [url=%s, found=%s]", url, found);
		}
	}

	static class FoundUrl {

		public final String name;
		public String path;
		public final String url;
		public final String pageUrl;

		@ConstructorProperties({ "name", "path", "url", "pageUrl" })
		public FoundUrl(String name, String path, String url, String pageUrl) {
			this.name = name;
			this.path = path;
			this.url = url;
			this.pageUrl = pageUrl;
		}

		public boolean dir() {
			return !url.contains("&file=");
		}

		@Override
		public String toString() {
			return String.format("Found [dir=%s, name=%s, url=%s]", dir(), name, url);
		}
	}

}