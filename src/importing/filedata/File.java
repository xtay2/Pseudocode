package importing.filedata;

import static misc.helper.ProgramHelper.*;
import static misc.helper.StringHelper.pointUnderline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import building.types.specific.FlagType;
import building.types.specific.KeywordType;
import errorhandeling.Errors;
import errorhandeling.PseudocodeException;
import formatter.basic.Formatter;
import importing.Importer;
import importing.filedata.interactable.CallInfo;
import importing.filedata.interactable.DefInfo;
import importing.filedata.paths.DataPath;
import importing.filedata.paths.FilePath;
import misc.helper.ProgramHelper;
import misc.helper.StringHelper;
import misc.supporting.FileManager;
import misc.supporting.Output;
import misc.util.Tuple;

public class File {

	public static final String EXTENSION = ".pc";

	/** The name of the Main.pc */
	public static final String MAIN_FILE = "Main";

	public final FilePath path;

	private final List<String> content;

	/** Set that contains all {@link DefInfo}s of this class. */
	private final Set<DefInfo> allDefs = new HashSet<>();

	/** Set that contains all called {@link DefInfo}s of this class. */
	private final Set<DefInfo> usedDefs = new HashSet<>();

	/** Set that contains all {@link CallInfo}s in all called {@link DefInfo}s in this class. */
	private final Set<CallInfo> usedCalls = new HashSet<>();

	/** Set that contains all imported filepaths. */
	private final Set<FilePath> imports = new HashSet<>();

	/**
	 * Creates a {@link File} and initialises {@link #allDefs}.
	 *
	 * @param path is a non null, valid {@link FilePath}.
	 */
	public File(FilePath path) {
		this.path = path;
		try {
			content = FileManager.readFile(path);
		} catch (IOException e) {
			throw new AssertionError("The creation of FilePath should already check, if this is a valid path.\n" + path, e);
		}
		if (!content.isEmpty()) { // Empty file has no info to init
			while (content.get(0).startsWith(KeywordType.IMPORT.toString())) {
				String importLn = content.remove(0).substring(KeywordType.IMPORT.toString().length() + 1);
				try {
					imports.add(new FilePath(importLn));
				} catch (IOException e) {
					throw new PseudocodeException(e, generateDataPath(0));
				}
				Output.print(this + " imports " + importLn);
			}
			preload();
		}
	}

	/**
	 * Finds all {@link DefInfo}s in this {@link File} and saves them in {@link #allDefs}.
	 */
	private void preload() {
		boolean isMain = path.getName().equals(File.MAIN_FILE);
		for (int i = 0; i < content.size(); i++) {
			String line = content.get(i);
			if (containsRunnable(line, "\\b" + KeywordType.FUNC + "\\b"))
				i = buildDefFromLine(i);
			else if (isMain && containsRunnable(line, "\\b" + KeywordType.MAIN + "\\b"))
				i = buildMainFromLine(i);
		}
	}

	/**
	 * Creates a {@link DefInfo} from a main-func in {@link #content} and adds it to {@link #allDefs}.
	 *
	 * @param lineIdx is the line in which the main-func begins.
	 * @return the last lineIdx of the def.
	 */
	private int buildMainFromLine(int lineIdx) {
		String line = content.get(lineIdx);
		int end = lineIdx;
		if (!containsRunnable(line, ";"))
			end = findMatchingBrack(content, lineIdx, indexOfRunnable(line, Formatter.OBR))[0];
		allDefs.add(new DefInfo(path, KeywordType.MAIN.toString(), 0, lineIdx, end, false));
		return end;
	}

	/**
	 * Creates a {@link DefInfo} from {@link #content} and adds it to {@link #allDefs}.
	 *
	 * @param lineIdx is the line in which the definition begins.
	 * @return the last lineIdx of the def.
	 */
	private int buildDefFromLine(int lineIdx) {
		String line = content.get(lineIdx);
		// Find if native
		boolean isNative = containsRunnable(line, FlagType.NATIVE + "\\s" + KeywordType.FUNC);
		// Find name
		String defName = getFirstRunnable(line, "(?<=" + KeywordType.FUNC + "\\s)\\w+(?=\\()");
		// Find args
		String params = getFirstRunnable(line, "\\([\\w,\\?\\[\\]\\s]*\\)");
		if (defName == null || params == null) {
			throw new PseudocodeException("IllegalCodeFormat", "Illegal use of the " + KeywordType.FUNC + " keyword."//
					+ " (Expected a name, followed by brackets)", line, generateDataPath(lineIdx));
		}
		int argCnt = params.length() == 2 ? 0 : runnableMatches(params, ",") + 1;
		// Find end
		int end = lineIdx;
		if (!isNative && !containsRunnable(line, ";"))
			end = findMatchingBrack(content, lineIdx, indexOfRunnable(line, Formatter.OBR))[0];
		if (allDefs.stream().anyMatch(def -> def.matches(defName, argCnt))) {
			throw new PseudocodeException("DuplicateDef", "There are multiple definitions of \"" + defName + "\".",
					generateDataPath(lineIdx));
		}
		allDefs.add(new DefInfo(path, defName, argCnt, lineIdx, end, isNative));
		return end;
	}

	/**
	 * Gets called by another {@link File}.
	 *
	 * <pre>
	 * This Method:
	 * -Tries to find matching {@link DefInfo}s for every {@link CallInfo}.
	 * -If all {@link DefInfo}s are found, this preloads every other {@link DefInfo} for every outgoing {@link CallInfo}.
	 * </pre>
	 *
	 * @param incoming are the incoming {@link CallInfo}s from other {@link File}s.
	 * @throws AssertionError if the main-func couldn't get found.
	 * @throws PseudocodeException if the any other func couldn't get found.
	 */
	public void findUsedDefs(CallInfo... incoming) {
		Set<DefInfo> newlyAddedDefs = new HashSet<>();
		for (CallInfo ci : incoming) {
			DefInfo target = allDefs.stream() //
					.filter(di -> di.matches(ci)) //
					.findFirst().orElseThrow( //
							() -> {
								// Error has to be handled here.
								if (ci.originPath() == null && ci.targetName().equals(KeywordType.MAIN.toString()))
									Errors.handleError(new AssertionError("Couldn't find " + KeywordType.MAIN + "-func in " + this));
								return new PseudocodeException("DefNotFound", //
										"Tried to call non-existent definition \"" + ci.targetName() + "\"." //
												+ "\nCallInfo: " + ci + "\nFileInfo: " + debugInfo(),
										ci.originPath());
							});
			if (usedDefs.add(target))
				newlyAddedDefs.add(target);
		}
		if (!newlyAddedDefs.isEmpty())
			findUsedCalls(newlyAddedDefs);
	}

	/**
	 * After one {@link File} that imports this one, found all the matching {@link DefInfo}s to its
	 * {@link CallInfo}s, all outgoing calls from these newly added {@link DefInfo}s.
	 */
	private void findUsedCalls(Set<DefInfo> newelyAddedDefs) {
		Set<CallInfo> newelyAddedCalls = new HashSet<>();
		for (DefInfo def : newelyAddedDefs) {
			if (!def.isNative())
				newelyAddedCalls.addAll(findCallsInDef(def));
		}
		if (!newelyAddedCalls.isEmpty())
			callAllFiles(newelyAddedCalls);
	}

	/**
	 * Sorts the newelyAddedCalls by {@link File} and then calls {@link #findUsedDefs(CallInfo...)} for
	 * every file.
	 */
	private void callAllFiles(Set<CallInfo> newelyAddedCalls) {
		List<CallInfo> sortedCalls = newelyAddedCalls.stream().sorted(CallInfo.compareByFile()).toList();
		File current = Importer.getFile(sortedCalls.get(0).targetFile());
		List<CallInfo> callsForFile = new ArrayList<>();
		// Group CallInfos
		for (CallInfo ci : sortedCalls) {
			if (!ci.targetFile().equals(current.path)) {
				// Send Calls to target-File
				current.findUsedDefs(callsForFile.toArray(CallInfo[]::new));
				current = Importer.getFile(ci.targetFile());
				callsForFile.clear();
			}
			callsForFile.add(ci);
		}
		current.findUsedDefs(callsForFile.toArray(CallInfo[]::new));
	}

	/** Finds a {@link Set} of calls in def and returns them as {@link CallInfo}s. */
	private Set<CallInfo> findCallsInDef(DefInfo def) {
		Set<CallInfo> calls = new HashSet<>();
		for (int i = def.startLine(); i <= def.endLine(); i++) {
			Set<String> matches = ProgramHelper.getAllCallsInLine(content.get(i));
			for (String match : matches) {
				int idxOfBrack = match.indexOf('(');
				String name = match.substring(0, idxOfBrack);
				int params = idxOfBrack == match.length() - 2 ? 0 : ProgramHelper.runnableMatches(match, ",") + 1;
				calls.add(new CallInfo(new DataPath(path, i), findDefOf(name, params, i), name, params));
			}
		}
		return calls;
	}

	/**
	 * Searches for the {@link FilePath} of the {@link File} that contains the {@link DefInfo} thats
	 * being called.
	 */
	private FilePath findDefOf(String callName, int params, int orgLine) {
		// Own Files
		if (allDefs.stream().anyMatch(e -> e.matches(callName, params)))
			return path;
		// Imported
		for (FilePath fp : imports) {
			File f = Importer.getFile(fp);
			if (f.allDefs.stream().anyMatch(e -> e.matches(callName, params)))
				return fp;
		}
		throw new PseudocodeException("DefNotFound", //
				"Trying to call a function \"" + callName + "\" that doesn't get imported.", //
				pointUnderline(content.get(orgLine), indexOfRunnable(content.get(orgLine), callName), callName.length()),
				generateDataPath(orgLine) //
		);
	}

	/**
	 * Returns a sublist of content without all uncalled defs.
	 */
	public List<Tuple<DataPath, String>> getRelevantContent() {
		ArrayList<Tuple<DataPath, String>> relevant = new ArrayList<>(content.size());
		for (int i = 0; i < content.size(); i++)
			relevant.add(new Tuple<DataPath, String>(new DataPath(path, i + 1), content.get(i)));
		// List of all uncalled defs that should get deleted. (Ordered and Reversed)
		List<DefInfo> delOrdRev = new ArrayList<>(allDefs);
		delOrdRev.removeAll(usedDefs);
		delOrdRev = delOrdRev.stream().sorted(new Comparator<DefInfo>() {
			@Override
			public int compare(DefInfo o1, DefInfo o2) {
				return Integer.compare(o2.startLine(), o1.startLine());
			}

		}).toList();
		for (DefInfo uncalled : delOrdRev) {
			relevant.subList(uncalled.startLine(), uncalled.endLine() + 1).clear();
		}
		return relevant;
	}

	/**
	 * Generates the {@link DataPath} for a specific line in this {@link File}.
	 *
	 * @param line is the index of the line in {@link #content}.
	 */
	private DataPath generateDataPath(int line) {
		return new DataPath(path, line + imports.size() + 1);
	}

	/**
	 * An enhance {@link #toString()}-method, that provides additional debug-info like all defs, and all
	 * used calls.
	 *
	 * @return a multiline {@link String}.
	 */
	public String debugInfo() {
		return toString() + "\nAll Defs: " + StringHelper.enumerate(allDefs) + "\nUsed Calls: " + StringHelper.enumerate(usedCalls);
	}

	@Override
	public String toString() {
		return "File[Path: " + path + ", TotalDefs: " + allDefs.size() + ", UsedCalls: " + usedCalls.size() + "]";
	}
}
