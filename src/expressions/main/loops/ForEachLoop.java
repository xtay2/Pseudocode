package expressions.main.loops;

import static helper.Output.print;
import static parsing.program.ExpressionType.NAME;

import datatypes.NumberValue;
import datatypes.Value;
import exceptions.parsing.IllegalCodeFormatException;
import expressions.abstractions.Expression;
import expressions.abstractions.Scope;
import expressions.abstractions.ValueHolder;
import expressions.normal.brackets.OpenScope;
import expressions.normal.containers.Name;
import expressions.normal.containers.Variable;
import expressions.special.DataType;
import interpreter.Interpreter;
import interpreter.VarManager;
import parsing.program.KeywordType;

public class ForEachLoop extends Scope implements Loop {

	private ValueHolder array = null;
	private Name elementName = null;

	public ForEachLoop(int line) {
		super(line, KeywordType.FOR);
		setExpectedExpressions(NAME);
	}

	/** [NAME] [CONTAINER] [OPEN_SCOPE] */
	@Override
	public void merge(Expression... e) {
		if (e.length != 3)
			throw new AssertionError("Merge on a for-each-loop has to contain three elements: element, container and opened scope.");
		elementName = (Name) e[0];
		VarManager.nameCheck(elementName.getName(), getOriginalLine());
		array = (ValueHolder) e[1];
		openScope = (OpenScope) e[2];
	}

	@Override
	public boolean execute(ValueHolder... params) {
		print("Executing For-Each-In-Loop.");
		NumberValue repetitions = NumberValue.ZERO;
		try {
			for (Value e : array.getValue().asVarArray()) { // Cast to Var-Array
				VarManager.registerScope(this);
				VarManager.registerVar(new Variable(lineIdentifier, DataType.VAR, elementName, e));
				VarManager.initCounter(this, repetitions, getOriginalLine());
				if (!Interpreter.execute(lineIdentifier + 1)) {
					VarManager.deleteScope(this);
					return false; // Wenn durch return im Block abgebrochen wurde rufe nichts dahinter auf.
				}
				repetitions = NumberValue.add(repetitions, NumberValue.ONE);
				VarManager.deleteScope(this);
			}
		} catch (ClassCastException e) {
			throw new IllegalCodeFormatException(getOriginalLine(), "Cannot iterate over anything other than an array.");
		}
		return Interpreter.execute(getEnd());
	}

	@Override
	public String getScopeName() {
		return "foreach" + getStart() + "-" + getEnd();
	}
}
