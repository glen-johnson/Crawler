package com.jacamars.crawler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Main {

	public static void main(String args[]) throws Exception {
		String path = "/home/glen/small_servers/fetchlist_17/subset_1/k15";
		path = "/home/glen";
		String folder = "homepages";
		int workers = 100; // set to 1 to debug single threaded
		int i = 0;
		String verify = "true";
		while(i < args.length) {
			switch(args[i]) {
			case "-path":
				path = args[i+1];
				i+=2;
				break;
			case "-workers":
				workers = Integer.parseInt(args[i+1]);
				i+=2;
				break;
			case "-folder":
				folder = args[i+1];
				i+=2;
				break;
			case "-verify":
				verify = args[i+1];
				i+=2;
				break;
			}
		}
		if(folder == null) {
			System.out.println("FATAL: folder not provided\n");
		}
		else {
			// System.out.println("FOLDER: " + path + "/" + folder);
			Main main = new Main(path,workers,folder,verify);
		}
		
	}
	
	public Main(String path, int nw, String folder, String verify) throws Exception {
	
		String file = path + "/ip_fetchlist.txt";
		File f = new File(file);
		if(! f.exists() || f.isDirectory()) { 
			System.out.println("FATAL: does not exist: " + file);
			System.exit(0);
		}
		
		
		String content = new String(Files.readAllBytes(Paths.get(file)),StandardCharsets.UTF_8);
		
		// build the hosts file (depricated = moved to calling script
		//String orig = new String(Files.readAllBytes(Paths.get("/etc/hosts.orig")),StandardCharsets.UTF_8);
		//StringBuilder combined = new StringBuilder(content);
		//combined.append(orig);
		//Files.write(Paths.get("/home/glen/hosts"),combined.toString().getBytes());
		
		String hpFolder = path + "/" + folder;
		f = new File(hpFolder);
		if(! f.exists()) {
			Boolean success = f.mkdir();
			if(success == false || ! f.exists()) {
				System.out.println("FATAL: cannot create folder: " + hpFolder);
				System.exit(0);
			}
		}
		
		
		CountDownLatch latch = new CountDownLatch(nw);
		
		// Make the workers 
		List<Worker> workers = new ArrayList();
		for (int i=0; i < nw; i++) {
			workers.add(new Worker(latch));
		}
		
		String [] urls = content.split("\n");
		int k = 0;
		
		// give them work
		for (String url : urls) {
			StringBuilder sb = new StringBuilder("http://");
			String [] parts = url.split("\t");
			sb.append(parts[1]);
			sb.append(",");
			sb.append(hpFolder);
			sb.append(",");
			sb.append(verify);

			Worker w = workers.get(k++);
			w.offer(sb.toString());
			if (k == workers.size()) k = 0;
		}
		
		// Start them
		for (Worker w : workers) {
			w.start();
		}

		// Wait for them
		long time = System.currentTimeMillis();
		latch.await();
		
		// save the timeouts and jsoup errors to files
		StringBuilder baddies = new StringBuilder("");
		StringBuilder timeouts = new StringBuilder("");
		int num_bads = 0;
		int num_timeouts = 0;
		for (Worker w : workers) {
			//badUrls += w.badUrls;
			for (String bad : w.badUrls) {
				baddies.append(bad);
				++num_bads;
			}
			for (String bad : w.timeoutUrls) {
				timeouts.append(bad);
				++num_timeouts;
			}
		}
		
		if(num_bads > 0) {
			String data = baddies.toString();
			file = path + "/temp_worker_bad.txt";
			f = new File(file);
			if(f.exists()) {
				f.delete();
			}
			Files.write(Paths.get(file),data.getBytes());
			//System.out.println("Bad links: ");
			//System.out.println(data);
		}
		
		if(num_timeouts > 0) {
			String data = timeouts.toString();
			file = path + "/temp_worker_timeouts.txt";
			f = new File(file);
			if(f.exists()) {
				f.delete();
			}
			Files.write(Paths.get(file),data.getBytes());
			//System.out.println("Timeout links: ");
			//System.out.println(data);
		}

		
		time = System.currentTimeMillis() - time;
		System.out.println("All done! in " + (time/1000) + " seconds");
		System.out.println("Number of bad urls: " + num_bads);
		System.out.println("Number of timeouts: " + num_timeouts);
	}
}
