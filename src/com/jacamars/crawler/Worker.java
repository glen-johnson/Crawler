package com.jacamars.crawler;

import java.io.File;
import java.net.URL;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;


public class Worker implements Runnable {

   private static final String windowsUserAgent = "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 7.0; InfoPath.3; .NET CLR 3.1.40767; Trident/6.0; en-IN)";
   private static final String crawlerUserAgent = "Mozilla/5.0 (compatible; GimmeUSAbot/1.0; +https://gimmeusa.com/crawler.html)";


    // timeout is in milliseconds
    private static final int timeout = 30 * 1000;
    
    public List<String> badUrls = new ArrayList<String>();
    public List<String> timeoutUrls = new ArrayList<String>();
    //public int badUrls = 0;
	
    static int threadCount = 1;

       
    int myCount = 0;
	Thread me;
	ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<String>();

	CountDownLatch latch;		
	

	public Worker(CountDownLatch latch) {
		myCount = threadCount++;
		this.latch = latch;
		// System.out.println("I have started: " + this);
		me = new Thread(this);
	}
	
	public void start() {
		me.start();
	}

	@Override
	public void run() {
		String url = null;
		String result = null;
    		// We run the 'big' fetchlist only on index0 and the 'seed' fetchlists on the fetchers
    		// For the 'big' fetchlist, use the Windows UserAgent and the Crawler UserAgent for 'seed'
    		String userAgent = crawlerUserAgent;
        	Boolean followRedirects = false;
        	Boolean verify = true;
		
    		try {
    			String host = InetAddress.getLocalHost().getHostName();
               		if(host != null && host.equals("index0")) {
    				userAgent = windowsUserAgent;
				followRedirects = true;
    			}
		} catch (UnknownHostException e) {
			System.out.println(e.getMessage());
		}
		// System.out.println("UserAgent: " + userAgent);

		while((url = queue.poll()) != null) {
			
			String [] parts = url.split(",");
			String link     = parts[0];
			String folder   = parts[1];
			String vs       = parts[2];
			if(vs != null && vs.contains("false")) {
				// System.out.println("VERIFICATION IS OFF");
				verify = false;
			}
			
			
			
			// System.out.println(myCount + " Going to work on: " + link);
			try {
					result = doTheWork(link, folder, userAgent, followRedirects, verify);
					if(result != null && result.length() > 1) {
						badUrls.add(result);
					}
			} catch (Exception e) {
				// System.out.println(e.getMessage());
				link = link.replaceAll("^https*:/+", "");
				String exception = link + "\t\t" + e.getMessage() + "\n";
				timeoutUrls.add(exception);
				//++badUrls;
				// badUrls.add(url);
			}
			// System.out.println(myCount + " All done with url: " +  url);
		}
		latch.countDown();
	}

	public void stop() {
		me.interrupt();
	}
	
	public void offer(String offering) {
		queue.offer(offering);
	} 

	
	//////////////////////////////////////////////////////////////////////
	
	private String doTheWork(String url, String folder, String userAgent, Boolean followRedirects, Boolean verify) throws Exception{

		String fileName = url.substring(7);
		fileName = fileName.replaceAll("[^A-Za-z0-9\\-.]", "_");
		fileName = folder + "/" + fileName;
		File f = new File(fileName);
		if(! f.exists()) {

			//System.out.println("Worker: " + url);
			
			StringBuilder output = new StringBuilder();
			
			// fetch the url
			int redirs = 4;
			Response response = null;
			
			
			String link = url.replaceAll("^https*:/+", "");
			
			String sub_domain   = link;
			String domain_name  = link;
			String path         = null;
			String robot_domain = null;
			
			if(link.contains("/")) {
				String[] pieces = link.split("/");
				domain_name = pieces[0];
				sub_domain = domain_name;
				path = pieces[1];
			}
			
			domain_name = domain_name.replaceAll("^www.", "");
			
			String orig_domain_name = domain_name;
			
			if(path != null && path.length() < 12 && path.contains("robots.txt")) {
				robot_domain = sub_domain;
				url = "http://" + domain_name + "/robots.txt";
			}
			// System.out.println("Worker: " + url);
			if(followRedirects == true)
				redirs = 8;
			
			int statusCode = 0;
			while(redirs > 0) {
				--redirs;
    				try {
					response = Jsoup.connect(url).followRedirects(false).validateTLSCertificates(false).userAgent(userAgent).timeout(timeout).execute();
				} catch (IOException e) {
					//System.out.println("IO Exception: " + e);
					String message = e.getMessage();
					if(message != null) {
				    		return(link + "\t\tIO Error: " + message + "\n");
					}
					else {
				    		return(link + "\t\tunknown IO Error" + "\n");
					}
				}
			   
				statusCode = response.statusCode();
				//System.out.println(statusCode + "  " + response.url());
				   
				if(statusCode < 300)
					break;
				   
				if(statusCode >= 400) {
					System.out.println("statusCode + " + response.url());
					return(link + "\t\t400_status_code\n");
				}
					   
				
				if(statusCode >= 300) {
					   
					String redir_url = response.header("location");
			    	   	//System.out.println("301 LOCATION: " + redir_url);
			    	   
			    	   	if(redir_url != null && ! redir_url.contains("http")) {
			    			String old_url = response.url().toString();
			    		  	if(old_url.endsWith("/")) {
			    				old_url = old_url.substring(0,old_url.length() - 1);
			    		  	}
			    		  	if(redir_url.startsWith("/")) {
			    				redir_url = redir_url.substring(1,redir_url.length());
			    		  	}
				    	  	redir_url = old_url + "/" + redir_url;
				    	  	// System.out.println("301 URL: " + redir_url);
			    	   	}
					redir_url = redir_url.toLowerCase();
				        if(redir_url.contains(domain_name)) {
				    		url = redir_url;
				    		//System.out.println("INTERNAL: " + link + " TO " + redir_url);
				        }
				        else if(followRedirects == false) {
				    		String reason = link + "\t\texternal redirect" + "\t" + redir_url + "\n";
				    		//System.out.println(reason);
				    		return(reason);
					}
					else {
				    		url = redir_url;
					}
			   	}
			}

	    		//System.out.println("Redirs complete: " + url);

			if(statusCode >= 300)
				return(link + "\t\ttoo_many_redirects\n");
			
			// if robots.txt request, save then done
			if(robot_domain != null) {
				String data = response.body();
				if(data != null && data.length() > 5) {
					fileName = folder + "/" + robot_domain;
					f = new File(fileName);
					Files.write(Paths.get(fileName),data.getBytes());
				}
				return("");
			}
			
			// change to > 0 to debug the redir code
			if(statusCode < 0) {
				System.out.println(statusCode + " " + url);
				return("");
			}
			
			Document doc = response.parse();
	       		//Document doc = Jsoup.connect(url).followRedirects(false).userAgent(userAgent).timeout(timeout).get();
	        
	        	// collect <meta name tag info in <head> section
	        	String meta = getMeta(doc);
			if(verify == true) {
	    			// System.out.println("TESTING META");
	        		if(meta == null || meta.length() < 10) {
	        			String reason = link + "\t\tno_meta_lines\n";
	        			return(reason);
				}
			}
	        	

	        
		        // These are as complete as HtmlToPlainText is
		        //getP(doc, output);
		        //getHeaders(doc, output);
		        //getLists(doc, output);
		        
		        // collect all visible tags in the DOM
		        HtmlToPlainText formatter = new HtmlToPlainText();
		        String plainText = formatter.getPlainText(doc);
		        if(plainText == null || plainText.length() < 10) {
		        	String result = link + "\t\tno_text\n";
		        	return(result);
		        }
		        	
	
		        
		        // collect all links and anchors
		        String links = getLinks(doc, domain_name);
			if(verify == true) {
			        if(links == null || links.length() < 10) {
		        		String result = link + "\t\tno_links\n";
		        		return(result);        	
				}
		        }
		        
			if(meta != null) {
		        	output.append("META_HEADERS:\n");
		        	output.append(meta);
			}

		        output.append("\n\nVISIBLE_TEXT:\n");
		        output.append(plainText);

			if(links != null) {
		        	output.append("\n\nALL_LINKS:\n");
		        	output.append(links);
			}
		        
		        String title = getTitle(doc);
		        if(title != null) {
		           output.append("\n\nTITLE:\n");
		           output.append(title);
		        }
		        
		        // save to a unique file
		        String data = output.toString();
		        //System.out.println(data);
		        Files.write(Paths.get(fileName),data.getBytes());
	        	return("");
		}
		return("");
	}
	
	private String getMeta(Document doc) {
		StringBuilder sb = new StringBuilder();
        Elements names = doc.select("meta");
        String data = null;
        
        
        Boolean text = false;
        for (Element name : names) {
        	if(name != null) {
        		sb.append(name);
        	    sb.append("\n");
        	    text = true;
        	}
        }
        if(text) {
        	sb.append("\n");
        	data = sb.toString();
        }
        return(data);
    }
	
	private String getLinks(Document doc, String orig_url) {
		StringBuilder sb = new StringBuilder();
		
		Boolean links_content = false;
        Elements links = doc.select("a[href]");
        Elements media = doc.select("[src]");
        Elements imports = doc.select("link[href]");
        
      

        String line = String.format("Media: (%d)\n",  media.size());
        sb.append(line);
        for (Element src : media) {
            if (src.tagName().equals("img")) {
            	line = String.format(" * %s: <%s> %sx%s (%s)\n",
                        src.tagName(), src.attr("abs:src"), src.attr("width"), src.attr("height"),
                        src.attr("alt"));
            	sb.append(line);
            }
            else {
            	line = String.format(" * %s: <%s>\n", src.tagName(), src.attr("abs:src"));
            	sb.append(line);
            }
        }

        line = String.format("\nImports: (%d)\n", imports.size());
        sb.append(line);
        for (Element link : imports) {
        	line = String.format(" * %s <%s> (%s)\n", link.tagName(),link.attr("abs:href"), link.attr("rel"));
        	sb.append(line);
        }

        line = String.format("\nLinks: (%d)\n", links.size());
        sb.append(line);
        for (Element link : links) {
        	line = String.format(" * a: <%s>  (%s)\n", link.attr("abs:href"), link);
        	sb.append(line);
        	if(links_content == false && line.contains(orig_url)) 
        	   links_content = true;
        }
        
        String data = null;
        if(links_content) {
        	sb.append("\n");
        	data = sb.toString();
        }
        return(data);
    }
	
	private void getP(Document doc, StringBuilder sb) {
        sb.append("META_HEADERS:\n");
		
		Elements elements = doc.body().select("p");
		if(! elements.isEmpty()) {
			sb.append("P:\n");
			for (Element element : elements) {
		        	String text = element.ownText();
		        	if(text != null && text.length() > 0) {
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
	}
	
	private String getTitle(Document doc) {
		StringBuilder sb = new StringBuilder();
        Elements names = doc.select("title");
        String data = null;
        
        
        Boolean text = false;
        for (Element name : names) {
        	if(name != null) {
        		sb.append(name);
        	    sb.append("\n");
        	    text = true;
        	}
        }
        if(text) {
        	sb.append("\n");
        	data = sb.toString();
        }
        return(data);
	}
	
	private void getHeaders(Document doc, StringBuilder sb) {
		Elements hTags = doc.select("h1, h2, h3, h4, h5, h6");
		Elements tags = hTags.select("h1");
		Boolean flag = true;
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("H1:\n");
		        			flag = false;
		        	        sb.append("META_HEADERS:\n");
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
		
		tags = hTags.select("h2");
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("H2:\n");
		        			flag = false;
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
		
		tags = hTags.select("h3");
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		            sb.append("META_HEADERS:\n");
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("H3:\n");
		        			flag = false;
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
		
		tags = hTags.select("h4");
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("H4:\n");
		        			flag = false;
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
		
		tags = hTags.select("h5");
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("H5:\n");
		        			flag = false;
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
		
		tags = hTags.select("h6");
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("H6:\n");
		        			flag = false;
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
	}
	
	private void getLists(Document doc, StringBuilder sb) {
		Elements hTags = doc.select("ul, li");
		Elements tags = hTags.select("li");
		Boolean flag = true;
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("LI:\n");
		        			flag = false;
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
		
		tags = hTags.select("ul");
		if(! tags.isEmpty()) {
			flag = true;
			for (Element tag : tags) {
		        	String text = tag.ownText();
		        	if(text != null && text.length() > 0) {
		        		if(flag) {
		        			sb.append("UL:\n");
		        			flag = false;
		        		}
		        		sb.append(text);
		        		sb.append("\n");
		        	}
			}
			sb.append("\n");
		}
	}
	
	


	/**
     * Format an Element to plain-text
     * @param element the root element to format
     * @return formatted text
     */
    public String getPlainText(Element element) {
        FormattingVisitor formatter = new FormattingVisitor();
        NodeTraversor traversor = new NodeTraversor(formatter);
        traversor.traverse(element); // walk the DOM, and call .head() and .tail() for each node

        return formatter.toString();
    }

    // the formatting rules, implemented in a breadth-first DOM traverse
    private class FormattingVisitor implements NodeVisitor {
        private static final int maxWidth = 1000;
        private int width = 0;
        private StringBuilder accum = new StringBuilder(); // holds the accumulated text

        // hit when the node is first seen
        public void head(Node node, int depth) {
            String name = node.nodeName();
            if (node instanceof TextNode)
                append(((TextNode) node).text()); // TextNodes carry all user-readable text in the DOM.
            else if (name.equals("li"))
                append("\n * ");
            else if (name.equals("dt"))
                append("  ");
            else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5", "tr"))
                append("\n");
        }

        // hit when all of the node's children (if any) have been visited
        public void tail(Node node, int depth) {
            String name = node.nodeName();
            if (StringUtil.in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5"))
                append("\n");
            else if (name.equals("a"))
                append(String.format(" <%s>", node.absUrl("href")));
        }

        // appends text to the string builder with a simple word wrap method
        private void append(String text) {
            if (text.startsWith("\n"))
                width = 0; // reset counter if starts with a newline. only from formats above, not in natural text
            if (text.equals(" ") &&
                    (accum.length() == 0 || StringUtil.in(accum.substring(accum.length() - 1), " ", "\n")))
                return; // don't accumulate long runs of empty spaces

            if (text.length() + width > maxWidth) { // won't fit, needs to wrap
                String words[] = text.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    boolean last = i == words.length - 1;
                    if (!last) // insert a space if not the last word
                        word = word + " ";
                    if (word.length() + width > maxWidth) { // wrap and reset counter
                        accum.append("\n").append(word);
                        width = word.length();
                    } else {
                        accum.append(word);
                        width += word.length();
                    }
                }
            } else { // fits as is, without need to wrap text
                accum.append(text);
                width += text.length();
            }
        }

        @Override
        public String toString() {
            return accum.toString();
        }
        
 
    }
}
