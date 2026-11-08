#!/usr/bin/env python3
"""DOM/JS coherence checks to prevent blank buttons and missing detail panels."""
from pathlib import Path
from html.parser import HTMLParser
import re
base=Path(__file__).resolve().parents[1]/"public"
class Scan(HTMLParser):
    def __init__(self):super().__init__();self.ids=set();self.pages=set()
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        if "id" in attrs:self.ids.add(attrs["id"])
        if "data-page" in attrs:self.pages.add(attrs["data-page"])
for html,js in [("index.html","app.js"),("admin.html","admin.js")]:
    p=Scan();p.feed((base/html).read_text(encoding="utf8"))
    code=(base/js).read_text(encoding="utf8")
    refs=set(re.findall(r"\$\('([^']+)'\)",code))
    missing=refs-p.ids
    assert not missing,f"{html}: JavaScript selectors without elements: {sorted(missing)}"
    if html=="index.html":
        panels={"songs","detail","story","publicStories","storyDetail","submit","downloads"}
        assert all(x+"Panel" in p.ids for x in panels),p.ids
        assert {"songs","story","submit","downloads"}<=p.pages,p.pages
        actions={"likeBtn","downloadLink","scoreLink","comments","storyLikeBtn",
          "storyDownload","storyComments","storyShare","browseStories",
          "storyCommentForm","storyForm","songForm","shareBtn"}
        assert actions<=p.ids,actions-p.ids
    else:
        assert {"tracksPanel","storiesPanel","commentsPanel","storyCommentsPanel"}<=p.ids
    print(f"PASS {html}: {len(refs)} JS selectors found, no dangling selectors")
