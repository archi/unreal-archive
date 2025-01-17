<#assign game=map.page.letter.gametype.game>
<#assign gametype=map.page.letter.gametype>

<#assign headerbg>${staticPath()}/images/games/${game.name}.png</#assign>

<#list map.map.attachments as a>
	<#if a.type == "IMAGE">
		<#assign headerbg=urlEncode(a.url)>
		<#break>
	</#if>
</#list>

<#assign ogDescription="${map.map.name}, a ${map.map.playerCount} player ${gametype.name} map for ${game.game.bigName}, created by ${map.map.author}">
<#assign ogImage=headerbg>

<#include "../../_header.ftl">
<#include "../../macros.ftl">

	<@heading bg=[headerbg]>
		<a href="${relPath(sectionPath + "/index.html")}">Maps</a>
		/ <a href="${relPath(game.path + "/index.html")}">${game.name}</a>
		/ <a href="${relPath(gametype.path + "/index.html")}">${gametype.name}</a>
		/ ${map.map.name}
	</@heading>

	<@content class="info">
		<div class="screenshots">
			<@screenshots attachments=map.map.attachments/>
		</div>

		<div class="info">

			<section class="meta">
				<h2>Map Information</h2>
				<div class="label-value">
					<label>Name</label><span>${map.map.name}</span>
				</div>
				<div class="label-value">
					<label>Game Type</label><span>
						<a href="${relPath(gametype.path + "/index.html")}">${map.map.gametype}</a>
					</span>
				</div>
				<div class="label-value">
					<label>Title</label><span>${map.map.title}</span>
				</div>
				<div class="label-value">
					<label>Author</label><span>${map.map.author}</span>
				</div>
				<div class="label-value">
					<label>Player Count</label><span>${map.map.playerCount}</span>
				</div>
				<div class="label-value">
					<label>Release (est.)</label><span>${dateFmtShort(map.map.releaseDate)}</span>
				</div>
				<div class="label-value">
					<label>Description</label><span>${map.map.description?replace("||", "<br/><br/>")}</span>
				</div>
				<div class="label-value">
					<label>File Size</label><span>${fileSize(map.map.fileSize)}</span>
				</div>
				<div class="label-value">
					<label>File Name</label><span>${map.map.originalFilename}</span>
				</div>
				<div class="label-value nomobile">
					<label>Hash</label><span>${map.map.hash}</span>
				</div>
			</section>

			<#if map.variations?size gt 0>
				<section class="variations">
					<h2>Variations</h2>
					<table>
						<thead>
						<tr>
							<th>Name</th>
							<th>Release Date (est)</th>
							<th>File Name</th>
							<th>File Size</th>
						</tr>
						</thead>
						<tbody>
							<#list map.variations as v>
							<tr>
								<td><a href="${relPath(v.path + ".html")}">${v.map.name}</a></td>
								<td>${v.map.releaseDate}</td>
								<td>${v.map.originalFilename}</td>
								<td>${fileSize(v.map.fileSize)}</td>
							</tr>
							</#list>
						</tbody>
					</table>
				</section>
			</#if>

			<@files files=map.map.files alsoIn=map.alsoIn otherFiles=map.map.otherFiles/>

			<@downloads downloads=map.map.downloads/>

		</div>

	</@content>

<#include "../../_footer.ftl">