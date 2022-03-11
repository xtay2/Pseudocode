package interpreting.modules.merger;

import static building.types.SuperType.*;
import static building.types.specific.BuilderType.*;
import static building.types.specific.ExpressionType.LITERAL;
import static building.types.specific.ExpressionType.NAME;
import static building.types.specific.FlagType.CONSTANT;
import static building.types.specific.FlagType.FINAL;
import static building.types.specific.FlagType.NATIVE;
import static building.types.specific.KeywordType.FUNC;
import static building.types.specific.KeywordType.IS;
import static building.types.specific.data.DataType.VAR;
import static building.types.specific.operators.InfixOpType.GREATER;
import static building.types.specific.operators.InfixOpType.LESS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import building.expressions.abstractions.Expression;
import building.expressions.abstractions.interfaces.Flaggable;
import building.expressions.abstractions.interfaces.ValueChanger;
import building.expressions.abstractions.interfaces.ValueHolder;
import building.expressions.main.CloseBlock;
import building.expressions.normal.BuilderExpression;
import building.expressions.normal.brackets.OpenBlock;
import building.expressions.normal.containers.Name;
import building.types.AbstractType;
import building.types.specific.AssignmentType;
import building.types.specific.BuilderType;
import building.types.specific.ExpressionType;
import building.types.specific.FlagType;
import building.types.specific.KeywordType;
import building.types.specific.data.ExpectedType;
import building.types.specific.operators.PrefixOpType;
import interpreting.exceptions.IllegalCodeFormatException;
import interpreting.program.ValueBuilder;
import runtime.exceptions.UnexpectedTypeError;

/**
 * Every build-Method should atleast remove the first element of the line.
 * 
 * The subclasses of this are all indirectly called by the switch-cases in {@link #build()}.
 */
public abstract class SuperMerger extends ExpressionMerger {

	/**
	 * Constructs an {@link ValueHolder} from the {@link AbstractType} of the first
	 * {@link BuilderExpression}.
	 */
	protected static ValueHolder buildVal() {
		BuilderExpression fst = line.get(0);
		BuilderExpression sec = line.size() > 1 ? line.get(1) : null;
		ValueHolder result = switch (fst.type) {
			case ExpressionType e:
				yield switch (e) {
					case LITERAL:
						yield ValueBuilder.stringToLiteral(line.remove(0).value);
					case NAME:
						if (sec != null) {
							if (sec.is(OPEN_BRACKET))
								yield ValueMerger.buildCall();
							if (sec.is(ARRAY_START))
								yield ValueMerger.buildArrayAccess();
							if (sec.is(ASSIGNMENT_TYPE))
								yield ValueMerger.buildAssignment();
							// Check if it is a def link, rather than a operation
							if (line.size() >= 4 && sec.is(LESS) && line.get(2).is(LITERAL) && line.get(3).is(GREATER))
								if (line.size() == 4 || (line.size() > 4 && !line.get(4).is(NAME) && !line.get(4).is(LITERAL)))
									yield ValueMerger.buildDefLink();
						}
						yield buildName();
				};
			case BuilderType b:
				yield switch (b) {
					case ARRAY_START:
						yield ValueMerger.buildArrayLiteral();
					case OPEN_BRACKET:
						if (sec != null && sec.is(EXPECTED_TYPE))
							yield ValueMerger.buildExplicitCast();
						yield ValueMerger.buildBracketedExpression();
					case MULTI_CALL_LINE:
						yield ValueMerger.buildMultiCall();
					// Non-Value-BuilderTypes
					default:
						throw new UnexpectedTypeError(orgLine, b);
				};
			case ExpectedType e:
				yield ValueMerger.buildDeclaration();
			case PrefixOpType p:
				yield OpMerger.buildPrefix();
			default:
				throw new UnexpectedTypeError(orgLine, fst.type);
		};
		// Check for follow-ups.
		if (!line.isEmpty()) {
			if (line.get(0).is(INFIX_OPERATOR))
				result = OpMerger.buildOperation(result);
			else if (line.get(0).is(POSTFIX_OPERATOR))
				result = OpMerger.buildPostfix((ValueChanger) result);
			else if (line.get(0).is(IS))
				result = ValueMerger.buildIsStatement(result);
		}
		return result;
	}

	/** Constructs an {@link Expression} from a {@link BuilderType}. This includes Scopes. */
	protected static Expression buildAbstract() {
		BuilderType type = (BuilderType) line.get(0).type;
		return switch (type) {
			// Simple BuilderTypes
			case OPEN_BLOCK -> buildOpenBlock();
			case CLOSE_BLOCK -> buildCloseBlock();
			// Complex BuilderTypes
			case ARRAY_START, OPEN_BRACKET, MULTI_CALL_LINE -> (Expression) buildVal();
			// Decorative BuilderTypes
			default -> throw new UnexpectedTypeError(orgLine, type);
		};
	}

	/** Constructs an {@link Expression} from a {@link KeywordType}. */
	protected static Expression buildKeyword() {
		KeywordType type = (KeywordType) line.get(0).type;
		return switch (type) {
			// Loops
			case FOR -> LoopMerger.buildForEach();
			case REPEAT -> LoopMerger.buildRepeat();
			case FROM -> LoopMerger.buildFromTo();
			case WHILE, UNTIL -> LoopMerger.buildConditional(type);
			// Statements
			case IF, ELIF, ANY, ELSE -> StatementMerger.buildConditional(type);
			case RETURN -> StatementMerger.buildReturn();
			// Callables
			case FUNC -> FuncMerger.buildFunc(false);
			case MAIN -> FuncMerger.buildMain();
			case IS, IMPORT -> throw new UnexpectedTypeError(orgLine, type);
		};
	}

	/** [{] */
	protected static OpenBlock buildOpenBlock() {
		line.remove(0);
		return new OpenBlock(lineID);
	}

	/** [}] */
	protected static CloseBlock buildCloseBlock() {
		line.remove(0);
		return new CloseBlock(lineID);
	}

	/** [NAME] **/
	protected static Name buildName() {
		return new Name(lineID, line.remove(0).value);
	}

	/** [Start] [ValueHolder] ((,) [ValueHolder])] [End] */
	protected static ValueHolder[] buildParts() {
		List<ValueHolder> parts = new ArrayList<>();
		line.remove(0); // Start
		do {
			Expression fst = line.get(0);
			if (!fst.is(MULTI_CALL_LINE) && !fst.is(CLOSE_BRACKET) && !fst.is(ARRAY_END))
				parts.add(buildVal());
		} while (line.remove(0).is(COMMA));
		return parts.toArray(new ValueHolder[parts.size()]);
	}

	/** [EXPECTED_TYPE] */
	protected static ExpectedType buildExpType() {
		return (ExpectedType) line.remove(0).type;
	}

	/**
	 * Super-Routine for all Flaggables.
	 * 
	 * @param flags are the flags. They get set by this Method.
	 */
	protected static Flaggable buildFlaggable() {
		Set<FlagType> flags = new HashSet<>();
		while (line.get(0).is(FLAG_TYPE)) {
			if (!flags.add((FlagType) line.remove(0).type))
				throw new IllegalCodeFormatException(orgLine, "Duplicate flag. Line: " + orgLine);
		}
		Flaggable f = null;
		// Declaration with optional flags
		if (line.get(0).type instanceof ExpectedType)
			f = ValueMerger.buildDeclaration();
		// Declaration of a constant without type and optional flags
		else if ((flags.contains(CONSTANT) || flags.contains(FINAL)) && line.get(0).is(NAME)) {
			line.add(0, new BuilderExpression(lineID, VAR));
			if (line.size() <= 2 || !line.get(2).is(AssignmentType.NORMAL))
				throw new IllegalCodeFormatException(orgLine, "A final variable has to be defined with a value at declaration.");
			f = ValueMerger.buildDeclaration();
		}
		// Definition-Declaration with optional flags
		else if (line.get(0).is(FUNC))
			f = FuncMerger.buildFunc(flags.remove(NATIVE));
		// FlagSpace
		else if (line.get(0).is(OPEN_BLOCK))
			f = StatementMerger.buildFlagSpace();
		else
			throw new UnexpectedTypeError(line.get(0).type);
		f.addFlags(flags);
		return f;
	}
}
