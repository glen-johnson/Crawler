http::/jsoup.org
Jsoup
This is the java code downloaded from http::/jsoup.org
I downloaded Jan 17, 2015, got version 1.8.3
which was released Aug 2, 2015

Homepage:         http::/jsoup.org
API               http://jsoup.org/apidocs/
Cookbook          http://jsoup.org/cookbook/
On-line tool:     http://try.jsoup.org/ (use the Fetch URL button)

SHORTCOMINGS:
1) Frames: does not. You have to identify a page that uses frames and run those links through the tool.
2) Description & Keywords are not in the output. Add 'meta' to the css list in FormattingVisitor class in HtmlToPlainText.java to capture all meta header lines.

********************************
my code
********************************
Both jsoup-1.8.3.jar and jsoup-1.8.3-sources.jar are in libs/
Only jsoup-1.8.3.jar (classes) are used. The sources is there if you 
need to modify the classes.

If you make a change to  the classes, File->Export to build the jar
then copy the jar to workspace/Crawler/libs, replacing the exising one.
When exporting, deselect the jar so it does not complain about exporting
to itself.

Crawler (Ben's threaded layer that runs Gsoup)
Workspace/Crawler source files are here:
/workspace/Crawler/src/com/jacamars/crawler
You can commit changes.

The class HtmlToPlainText in Jsoup has been replaced in Crawler
so dont bother making changes to that class in Jsoup.
Make changes inside the methods called.

Bitbucket (bfaul/crawler)
mkdir Crawler
cd Crawler
git clone git@bitbucket.org:bfaul/crawler .

To build and run:
cd workspace/Crawler
ant onejar
java -jar -Xmx4g crawler-all.jar -folder /home/glen/small_servers/../b15 -workers 100



