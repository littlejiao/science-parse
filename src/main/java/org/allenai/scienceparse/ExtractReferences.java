package org.allenai.scienceparse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExtractReferences {
	
	public static abstract class BibStractor {
		public abstract List<BibRecord> parse(String source);
		public abstract String getCiteRegex();
		public abstract String getCiteDelimiter();
		final BibRecordParser recParser;
		BibStractor(BibRecordParser rp) {
			recParser = rp;
		}
	}
	
	public static interface BibRecordParser {
		public BibRecord parseRecord(String line);
	}
	
	private static List<BibStractor> extractors = Arrays.asList(new BracketNumber(), new NamedYear());
	
	private static class DefaultBibRecordParser implements BibRecordParser{
		public BibRecord parseRecord(String line) {
			return new BibRecord(line, null, null, null, 0);
		}
	}

	private static int extractRefYear(String sYear) {
		String yearPattern = " [1-2][0-9][0-9][0-9]";
		Matcher mYear = Pattern.compile(yearPattern).matcher(sYear);
		int a = 0;
		while(mYear.find()) {
			try {
				a = Integer.parseInt(mYear.group().trim());
			} catch(Exception e) {};
			if(a > BibRecord.MINYEAR && a < BibRecord.MAXYEAR)
				return a;
		}
		return a;
	}
	
	private static class InitialFirstQuotedBibRecordParser implements BibRecordParser{
		//example:
	//	"[1] E. Chang and A. Zakhor, “Scalable video data placement on parallel disk "
//				+ "arrays,” in IS&T/SPIE Int. Symp. Electronic Imaging: Science and Technology, "
//				+ "Volume 2185: Image and Video Databases II, San Jose, CA, Feb. 1994, pp. 208–221."
		public BibRecord parseRecord(String line) {
			String regEx = "\\[([0-9]+)\\] (.*), \\p{Pi}(.*),\\p{Pf} (?:(?:I|i)n )?(.*)\\.?";
			Matcher m = Pattern.compile(regEx).matcher(line.trim());
			if(m.matches()) {
				BibRecord out = new BibRecord(m.group(3), authorStringToList(m.group(2)),
						m.group(4), m.group(1), extractRefYear(m.group(4)));
						return out;
			}
			else
				return null;
		}
	}
	
	private static class AuthorYearBibParser implements BibRecordParser {
		//example:
		//STONEBREAKER, M. 1986. A Case for Shared Nothing. Database Engineering 9, 1, 4–9.
		
		public BibRecord parseRecord(String line) {
			String regEx = "([\\p{L}\\p{P}\\., ]+) ([0-9]{4}[a-z]?)\\. ([^\\.]+)\\. (?:(?:I|i)n )?(.*)\\.?";
			Matcher m = Pattern.compile(regEx).matcher(line.trim());
			if(m.matches()) {
				List<String> authors = authorStringToList(m.group(1));
				int year = Integer.parseInt(m.group(2).substring(0, 4));
				String citeStr = NamedYear.getCiteAuthorFromAuthors(authors) + ", " + year;
				BibRecord out = new BibRecord(m.group(3), authors,
						m.group(4), citeStr, year);
//				BibRecord out = new BibRecord("title", null, null, null, 0);
				
						return out;
			}
			else
				return null;
		}
		
	}

	/**
	 * Takes in a string mentioning several authors, returns normalized list of authors
	 * @param authString
	 * @return
	 */
	public static List<String> authorStringToList(String authString) {
		//figure out whether M. Johnson or Johnson, M.:
		boolean firstLast = false;
		List<String> out = new ArrayList<>();
		if(Pattern.compile("\\p{Lu}\\..*").matcher(authString).matches()) {
			firstLast = true;
		}
		log.info("auth string: " + authString);
		String [] names = authString.split("(,|( and ))+");
		log.info("names: " + Arrays.toString(names));
		if(firstLast) {
			out = Arrays.asList(names);
		}
		else {
			for(int i=0; i<names.length; i+=2) {
				if(names.length > i+1)
					out.add(names[i+1].trim() + " " + names[i].trim());
				else
					out.add(names[i].trim()); //hope for the best
			}
		}
		log.info("out: " + out.toString());
		return out;
	}
	
	private static <T> List<T> removeNulls(List<T> in) {
		List<T> out = new ArrayList<T>();
		for(T a : in) {
			if(a != null)
				out.add(a);
		}
		return out;
	}
	
	private static String getAuthorLastName(String authName) {
		int idx = authName.lastIndexOf(" ");
		return authName.substring(idx+1);
	}
	
	private static class NamedYear extends BibStractor {
		private final String citeRegex = "\\[(, [0-9]{4})+\\]";
		private final String citeDelimiter = ";";
		
		NamedYear() {
			super(new AuthorYearBibParser());
		}
		
		public String getCiteRegex() {
			return citeRegex;
		}
		
		public String getCiteDelimiter() {
			return citeDelimiter;
		}
	
		public static String getCiteAuthorFromAuthors(List<String> authors) {
			if(authors.size() > 2) {
				return getAuthorLastName(authors.get(0)) + " et al.";
			}
			else if(authors.size() == 1) {
				return getAuthorLastName(authors.get(0));
			}
			else if(authors.size() == 2) {
				return getAuthorLastName(authors.get(0)) + " and " + getAuthorLastName(authors.get(1));
			}
			return null;
		}
		
		public List<BibRecord> parse(String line) {
			if(line.startsWith("<bb>"))
				line = line.substring(4);
			String [] citesa = line.split("<bb>");
			List<String> cites = Arrays.asList(citesa);
			log.info(cites.get(0));
			List<BibRecord> out = new ArrayList<BibRecord>();
			for(String s : cites) {
				out.add(this.recParser.parseRecord(s));
			}
			out = removeNulls(out);
			return out;
		}
		
	}
	
	private static class BracketNumber extends BibStractor {
		private final String citeRegex = "\\[([0-9,]+)\\]";
		private final String citeDelimiter = ",";
		
		BracketNumber() {
			super(new InitialFirstQuotedBibRecordParser());
		}
		
		public String getCiteRegex() {
			return citeRegex;
		}
		
		public String getCiteDelimiter() {
			return citeDelimiter;
		}
		
		public List<BibRecord> parse(String line) {
			line = line.replaceAll("<bb>", "");
			int i=0;
			String tag = "[" + (++i) + "]";
			List<String> cites = new ArrayList<String>();
			while(line.contains(tag)) {
				int st = line.indexOf(tag);
				tag = "[" + (++i) + "]";
				int end = line.indexOf(tag);
				if(end > 0) {
					cites.add(line.substring(st, end));
				}
				else {
					cites.add(line.substring(st));
				}
			}
			List<BibRecord> out = new ArrayList<BibRecord>();
			for(String s : cites) {
				out.add(this.recParser.parseRecord(s));
			}
			return out;
		}
	}
	
	private static int refStart(List<String> paper) {
		for(int i=0; i<paper.size(); i++) {
			String s = paper.get(i);
			if(s.endsWith("References")||s.endsWith("Citations")||s.endsWith("Bibliography")||
					s.endsWith("REFERENCES")||s.endsWith("CITATIONS")||s.endsWith("BIBLIOGRAPHY"))
				return i;
		}
		return -1;
	}
	
	public static int longestIdx(List<BibRecord> [] results) {
		int maxLen = -1;
		int idx = -1;
		for(int i=0; i<results.length; i++) {
			if(results[i].size() > maxLen) {
				idx = i;
				maxLen = results[i].size();
			}
		}
		return idx;
	}
	
	public static List<BibRecord> findReferences(List<String> paper) {
		int start = refStart(paper) + 1;
		List<BibRecord> [] results = new ArrayList[extractors.size()];
		for(int i=0; i<results.length; i++)
			results[i] = new ArrayList<BibRecord>();
		StringBuffer sb = new StringBuffer();
		for(int i=start; i<paper.size(); i++) {
			sb.append("<bb>" + paper.get(i));
		}
		String text = sb.toString();
		for(int i=0; i<results.length; i++) {
			results[i] = extractors.get(i).parse(text);
		}
		int idx = longestIdx(results);
		return results[idx];
	}
	
	public static List<CitationRecord> findCitations(List<String> paper, List<BibRecord> bib) {
		ArrayList<CitationRecord> out = new ArrayList<>();
		return out;
	}
}
