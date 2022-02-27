package modules.interpreter;

import static helper.Output.UNDERLINE;
import static helper.Output.print;

import datatypes.Value;
import exceptions.parsing.IllegalCodeFormatException;
import exceptions.runtime.DeclarationException;
import expressions.abstractions.GlobalScope;
import expressions.abstractions.MainExpression;
import expressions.abstractions.interfaces.ValueHolder;
import expressions.main.functions.MainFunction;
import expressions.main.functions.Returnable;
import expressions.main.statements.ReturnStatement;
import expressions.normal.containers.Variable;
import expressions.possible.assigning.Declaration;
import main.Main;
import modules.parser.program.ProgramLine;
import types.specific.KeywordType;

public final class Interpreter {
	/**
	 *
	 * Registeres every {@link Variable} and {@link Returnable} and starts the interpreting-process by
	 * calling the {@link MainFunction}.
	 * 
	 * @param program is the program that gets interpreted.
	 * 
	 * @see Main#main
	 */
	public static void interpret() {
		// INIT
		registerFunctions();
		registerGlobalVars();
		// RUNTIME
		print("\nStarting Program: " + UNDERLINE);
		execute(FuncManager.getLine("main"));
	}

	/**
	 * Calls a function and returns it return-value.
	 *
	 * @param name          is the name of the function.
	 * @param doExecuteNext if the called function should execute the one in the following line.
	 *                      Standart: true
	 * @param params        are the function-parameters
	 * @return the return-value of the function.
	 */
	public static Value call(String name, ValueHolder... params) {
		Returnable f = (Returnable) Main.PROGRAM.getLine(FuncManager.getLine(name + params.length)).getMainExpression();
		f.execute(params);
		return f.getValue();
	}

	/**
	 * Executes a MainExpression.
	 *
	 * @param i      the line of the MainExpression.
	 * @param params are the passed parameters
	 * 
	 * @return false if this function shouldn't call any other functions afterwards.
	 *         {@link ReturnStatement#execute}
	 */
	public static boolean execute(int i, ValueHolder... params) {
		return Main.PROGRAM.getLine(i).getMainExpression().execute(params);
	}

	/**
	 * Register all functions in the program, so that they are accessable through the call-Method.
	 * 
	 * @see Interpreter#call(String, ValueHolder...)
	 */
	private static void registerFunctions() {
		print("Pre-Compiling:" + UNDERLINE);
		boolean hasMain = false;
		for (ProgramLine l : Main.PROGRAM) {
			int lineID = l.lineID, orgLine = l.orgLine;
			MainExpression e = l.getMainExpression();
			// Check func in other func
			if (e instanceof Returnable r && r.getOuterScope() != GlobalScope.GLOBAL)
				throw new IllegalCodeFormatException(orgLine, "A function cannot be defined in another function. \nSee: \""
						+ r.getNameString() + "\" in " + r.getOuterScope().getScopeName());
			// Check doppelte Main
			if (e instanceof MainFunction) {
				if (hasMain)
					throw new DeclarationException(orgLine, "The main-function should be defined only once!");
				FuncManager.registerFunction(KeywordType.MAIN.toString(), lineID);
				hasMain = true;
			}
			// Speichere alle Funktionsnamen (Main darf nicht gecallt werden.)
			if (e instanceof Returnable r)
				FuncManager.registerFunction(r.getNameString() + r.expectedParams(), lineID);
		}
		if (!hasMain)
			throw new AssertionError("Program has to include a main-function!");
	}

	/**
	 * Registers and initialises all static Variables in the global scope.
	 * 
	 * (Outside of functions).
	 * 
	 * @see ProgramLine
	 * @see Declaration
	 */
	private static void registerGlobalVars() {
		for (ProgramLine l : Main.PROGRAM) {
			if (l.getMainExpression() instanceof Declaration d && d.getScope() == GlobalScope.GLOBAL) {
				print("Registering global Var " + d.getNameString() + " in line: " + d.getOriginalLine());
				d.getValue();
			}
		}
	}
}
