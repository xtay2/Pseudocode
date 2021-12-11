package parser.program;

import java.math.BigDecimal;
import java.util.ArrayList;

import datatypes.ArrayValue;
import datatypes.BoolValue;
import datatypes.Castable;
import datatypes.NumberValue;
import datatypes.TextValue;
import exceptions.DeclarationException;
import expressions.main.Call;
import expressions.main.functions.Function;
import expressions.normal.CloseBracket;
import expressions.normal.Comma;
import expressions.normal.Literal;
import expressions.normal.Name;
import expressions.normal.OpenBracket;
import expressions.normal.Variable;
import expressions.normal.array.ArrayAccess;
import expressions.normal.array.ArrayEnd;
import expressions.normal.array.ArrayStart;
import expressions.normal.operators.Operation;
import expressions.normal.operators.Operator;
import expressions.special.Expression;
import expressions.special.Type;
import expressions.special.Value;
import expressions.special.ValueHolder;

public final class ValueBuilder {

	private static ArrayList<Expression> list;

	private static int lineIndex;

	public static ArrayList<Expression> buildLine(ArrayList<Expression> l, int line) {
		list = l;
		lineIndex = line;
		for (int i = 0; i < list.size(); i++)
			if (list.get(i) instanceof Literal || list.get(i) instanceof Name)
				build(i, false);
		for (int i = 0; i < list.size(); i++) {
			// For Literal Arrays
			if (list.get(i) instanceof ArrayStart && !(list.get(i - 1) instanceof Variable))
				buildArray(i);
		}
		return list;
	}

	/**
	 * Recursivly builds an expression.
	 *
	 * @param i                 is the index in list.
	 * @param buildingOperation default is false.
	 */
	private static ValueHolder build(int i, boolean buildingOperation) {
		if (list.get(i) instanceof ValueHolder) {
			if (list.get(i) instanceof Literal) {
				// LITERAL with following OPERATOR
				if (i + 1 < list.size() && list.get(i + 1) instanceof Operator) {
					Operation op = new Operation(i, buildOperation(i));
					list.add(i, op);
					return op;
				}
				// LITERAL without following OPERATOR
				else
					return (Literal) list.get(i);
			}
			if (i + 1 < list.size() && list.get(i)instanceof Name name)
				// NAME without following OPERATOR
				if (list.get(i + 1) instanceof Comma || list.get(i + 1) instanceof CloseBracket)
					return name;
				else if (list.get(i + 1)instanceof Operator op) {
					Operation operation = new Operation(i, buildOperation(i));
					list.add(i, operation);
					return operation;
				}
				// FUNCTION_CALL
				else if (list.get(i + 1) instanceof OpenBracket && !(list.get(0) instanceof Function)) {
					Call c = new Call(lineIndex);
					c.init(name.getName(), buildParams(i + 2));
					list.set(i, c); // Name
					list.remove(i + 1);
					// OPERATOR nach CALL
					if (i + 1 < list.size() && list.get(i + 1) instanceof Operator && !buildingOperation) {
						Operation op = new Operation(lineIndex, buildOperation(i));
						list.add(i, op);
						return op;
					}
					return c;
				}
				// ARRAY_ACCESS
				else if (list.get(i + 1) instanceof ArrayStart && list.get(i + 2) instanceof ValueHolder) {
					ArrayAccess arr = new ArrayAccess(lineIndex, name.getName(), build(i + 2, false));
					list.set(i, arr); // Name
					list.remove(i + 1);// ArrayStart
					list.remove(i + 1);// Index
					list.remove(i + 1);// ArrayEnd
					// ARRAY_ACCESS with following operator
					if (i + 1 < list.size() && list.get(i + 1) instanceof Operator) {
						Operation op = new Operation(i, buildOperation(i));
						list.add(i, op);
						return op;
					}
					return arr;
				}
		}
		return null;
	}

	private static ArrayList<ValueHolder> buildParams(int pos) {
		ArrayList<ValueHolder> params = new ArrayList<>();
		for (int i = pos; i < list.size(); i++) {
			Expression current = list.get(i);
			if (current instanceof CloseBracket)
				break;
			if (current instanceof Name || current instanceof Literal || current instanceof ArrayAccess) {
				ValueHolder p = build(i, false);
				params.add(p);
			} else if (current instanceof ArrayStart)
				params.add(buildArray(i));
		}
		while (!(list.get(pos) instanceof CloseBracket))
			list.remove(pos);
		list.remove(pos);
		return params;
	}

	private static ValueHolder buildArray(int index) {
		ArrayList<ValueHolder> values = new ArrayList<>();
		if (list.get(index)instanceof ArrayStart s)
			list.remove(index);
		while (!(list.get(index) instanceof ArrayEnd)) {
			if (list.get(index)instanceof ValueHolder v) {
				if (index + 1 < list.size() && list.get(index + 1) instanceof Operator) {
					Operation op = new Operation(index, buildOperation(index));
					values.add(op);
				} else {
					values.add(v);
					list.remove(index);
				}
			} else if (list.get(index)instanceof ArrayStart s) {
				list.remove(index);
				values.add(buildArray(index));
			} else if (list.get(index) instanceof Comma)
				list.remove(index);
			else
				throw new DeclarationException(list.get(index) + " isn't allowed in an Array Declaration.");
		}
		list.set(index, new Literal(new ArrayValue(Type.VAR_ARRAY, values), lineIndex));
		if (index + 1 < list.size() && list.get(index + 1) instanceof Operator) {
			Operation op = new Operation(lineIndex, buildOperation(index));
			list.add(index, op);
		}
		return (ValueHolder) list.get(index);
	}

	private static ArrayList<Expression> buildOperation(int pos) {
		ArrayList<Expression> operation = new ArrayList<>();
		for (int i = pos; i < list.size(); i++) {
			Expression current = list.get(i);
			if (current instanceof Literal || current instanceof Call || current instanceof Operator || current instanceof ArrayAccess) {
				operation.add(current);
			} else if (current instanceof Name) {
				if (i + 1 < list.size() && (list.get(i + 1) instanceof OpenBracket || list.get(i + 1) instanceof ArrayStart)) {
					operation.add((Expression) build(i, true));
				} else
					operation.add(current);
			} else if (current instanceof ArrayStart) {
				operation.add((Expression) buildArray(i));
			} else
				break;
		}
		for (int i = 0; i < operation.size(); i++)
			list.remove(pos);
		return operation;
	}

	public static Castable buildLiteral(String arg) {
		// Is Single Value
		if (Value.isBoolean(arg))
			return new BoolValue("true".equals(arg));
		if (Value.isNumber(arg))
			return new NumberValue(new BigDecimal(arg));
		if (Value.isString(arg))
			return new TextValue(arg.substring(1, arg.length() - 1));
		throw new AssertionError("Type must be known by now!");
	}

}