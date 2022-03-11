package interpreting.program;

import static building.types.SuperType.EXPECTED_TYPE;
import static building.types.SuperType.FLAG_TYPE;
import static building.types.SuperType.KEYWORD_TYPE;
import static building.types.SuperType.PREFIX_OPERATOR;
import static building.types.specific.BuilderType.CLOSE_BLOCK;
import static building.types.specific.BuilderType.MULTI_CALL_LINE;
import static building.types.specific.ExpressionType.NAME;
import static building.types.specific.KeywordType.ANY;
import static building.types.specific.KeywordType.ELIF;
import static building.types.specific.KeywordType.ELSE;
import static building.types.specific.KeywordType.IF;
import static interpreting.program.StringConverter.constructExpression;
import static interpreting.program.StringConverter.isNewExpression;

import java.util.ArrayList;
import java.util.List;

import building.expressions.abstractions.MainExpression;
import building.expressions.main.functions.Function;
import building.expressions.main.statements.ConditionalStatement;
import building.expressions.main.statements.ReturnStatement;
import building.expressions.normal.BuilderExpression;
import building.types.AbstractType;
import interpreting.exceptions.IllegalCodeFormatException;
import interpreting.modules.merger.ExpressionMerger;
import interpreting.modules.parser.Parser;
import misc.main.Main;

public class ProgramLine {

	final List<BuilderExpression> expressions = new ArrayList<>();
	final String line;

	/** The original line from the users editor. */
	public final int orgLine;

	/** The unique lineID that this program generated. */
	public final int lineID;

	private MainExpression main;

	/**
	 * Save a line of code and build its object-expression-representation.
	 *
	 * @param line    is the content of this line of code.
	 * @param lineID  is the unique identifier.
	 * @param orgLine is the line from the users editor.
	 */
	public ProgramLine(String line, int lineID, int orgLine) {
		this.line = line;
		this.lineID = lineID;
		this.orgLine = orgLine;
	}

	/**
	 * Reads the line and constructs an object-expression-notation from the information.
	 */
	void construct() {
		String current = "";
		// Erwartete Ausdr�cke am Zeilenanfang
		AbstractType expectedTypes[] = { KEYWORD_TYPE, EXPECTED_TYPE, FLAG_TYPE, NAME, CLOSE_BLOCK, PREFIX_OPERATOR, MULTI_CALL_LINE };
		boolean inString = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (inString && c == '\\') {
				current += c + "" + line.charAt(i + 1);
				i++;
				continue;
			}

			// Checke nach single line comments
			if (!inString && c == Parser.SINGLE_LINE_COMMENT)
				break;

			if (!inString)
				current = current.strip();

			// Neue Expression wenn c ' ', ',' oder '(' ist.
			if (!current.isBlank() && !inString && isNewExpression(current, c, expectedTypes)) {
				expectedTypes = constructExpression(current.strip(), expectedTypes, this);
				current = "";
			}
			// Teste nach Stringgrenzen
			if (c == '"')
				inString = !inString;
			current += c;
		}
		if (inString)
			throw new IllegalCodeFormatException(orgLine, "String has to be closed.");
		if (!current.strip().isEmpty()) // Wenn noch ein einzelnes Zeichen am Zeilenende steht.
			constructExpression(current.strip(), expectedTypes, this);
		if (expressions.isEmpty())
			throw new AssertionError("Line has to contain atleast one Expression.");
	}

	/** Returns the last IfStatement or ElifStatement. */
	private ConditionalStatement findLastIf() {
		if (lineID == 0)
			throw new IllegalCodeFormatException(orgLine, "An elif-, any- or else-statement needs a preceding IfStatement.");
		MainExpression previous = Main.PROGRAM.getLine(lineID - 1).getMainExpression();
		if (previous.is(IF) || previous.is(ELIF) || previous.is(ANY))
			return (ConditionalStatement) previous;
		return Main.PROGRAM.getLine(lineID - 1).findLastIf();
	}

	/** Merges the {@link MainExpression} from the constructed {@link #expressions}. */
	void merge() {
		main = ExpressionMerger.merge(this);
		expressions.clear();
		// Wenn es ein Returnstatement ist, suche die Funktion
		if (main instanceof ReturnStatement)
			((ReturnStatement) main).initFunc(Main.PROGRAM.getLine(lineID - 1).searchForFunc());
		// Connect Conditional blocks.
		else if (main.is(ELIF) || main.is(ANY) || main.is(ELSE))
			findLastIf().setNextBlock((ConditionalStatement) main);
	}

	/**
	 * Recursivly searches for func-declaration. Breaks when encountering the start of the file.
	 */
	private Function searchForFunc() {
		if (main instanceof Function)
			return (Function) main;
		if (lineID == 0)
			throw new IllegalCodeFormatException(orgLine, "Return-Statement has to be declared inside a function.");
		return Main.PROGRAM.getLine(lineID - 1).searchForFunc();
	}

	/** Returns the constructed but unmerged {@link #expressions}. */
	public List<BuilderExpression> getExpressions() {
		if (expressions.isEmpty())
			throw new AssertionError("The expressions are either already merged or not even constructed at this point. " + "\nExpressions: "
					+ expressions + "\nLine: " + line + "\nMainExp: " + main);
		return new ArrayList<>(expressions);
	}

	/** Returns the {@link MainExpression} of this line. */
	public MainExpression getMainExpression() {
		if (main != null)
			return main;
		throw new AssertionError("MainExpression of line " + orgLine + " is null at this point.");
	}

	@Override
	public String toString() {
		return lineID + "\t" + line;
	}

}
