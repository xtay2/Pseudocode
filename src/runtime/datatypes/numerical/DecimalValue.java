package runtime.datatypes.numerical;

import static building.types.specific.datatypes.SingleType.*;
import static misc.helper.MathHelper.*;
import static runtime.datatypes.numerical.ConceptualNrValue.*;

import java.math.*;
import java.util.*;

import building.expressions.abstractions.interfaces.*;
import building.types.specific.datatypes.*;
import ch.obermuhlner.math.big.*;
import errorhandeling.*;
import misc.supporting.*;
import runtime.datatypes.*;
import runtime.datatypes.textual.*;
import runtime.natives.*;

/** An arbitrary Decimal Number with 100 digits of precision. */
public final class DecimalValue extends NumberValue {
	
	/**
	 * The Numerator
	 *
	 * <pre>
	 * Special case:
	 *
	 * - Can never be {@link BigInteger#ZERO},
	 *   because then it would be {@link NumberValue#ZERO}.
	 * </pre>
	 */
	protected final BigInteger num;
	
	/**
	 * The Denominator
	 *
	 * <pre>
	 * Special cases:
	 *
	 * - Can never be {@link BigInteger#ONE},
	 *   because then it would be an {@link IntValue}.
	 *
	 * - Can never be {@link BigInteger#ZERO},
	 *   because then it would be {@link ConceptualNrValue#NAN}.
	 * </pre>
	 */
	protected final BigInteger denom;
	
	/** Produces a rational Number. This fraction has to be already reduced. */
	protected DecimalValue(BigInteger numerator, BigInteger denominator) {
		super(NR);
		// Assertions
		assert !denominator.equals(BigInteger.ZERO) : "Denominator cannot be zero, use NaN instead.";
		assert !numerator.equals(BigInteger.ZERO) : "Nominator cannot be zero, use ZERO instead.";
		assert !denominator.equals(BigInteger.ONE) : "Denominator cannot be one, use an IntValue instead.";
		// Length check
		if (getDigitCount(numerator) > MAX_LENGTH || getDigitCount(denominator) > MAX_LENGTH)
			throw new ArithmeticException("Numbers cannot extend 100 digits.");
		// Sign Check I: -x / -y = x / y
		if (numerator.compareTo(BigInteger.ZERO) < 0 && denominator.compareTo(BigInteger.ZERO) < 0) {
			numerator = numerator.abs();
			denominator = denominator.abs();
		}
		// Sign Check II: x / -y = -x / y
		else if (numerator.compareTo(BigInteger.ZERO) > 0 && denominator.compareTo(BigInteger.ZERO) < 0) {
			numerator = numerator.negate();
			denominator = denominator.abs();
		}
		this.num = numerator;
		this.denom = denominator;
	}
	
	/**
	 * Turns this into a String.
	 *
	 * <pre>
	 * Example 1: n = 1, d = 2 Output: "0.5"
	 *
	 * Example 2: n = 2, d = 3 Output: "0.(6)"
	 *
	 * used in {@link #toString()} and {@link #asText()}
	 * </pre>
	 *
	 * @param n numerator
	 * @param d denominator
	 */
	protected String fractionToDecimal() {
		StringBuilder sb = new StringBuilder();
		if (isNegative())
			sb.append('-');
		BigInteger n = num.abs(), d = denom.abs();
		BigInteger rem = n.remainder(d);
		sb.append(n.divide(d));
		sb.append('.');
		final HashMap<BigInteger, Integer> map = new HashMap<>();
		while (!rem.equals(BigInteger.ZERO)) {
			if (map.containsKey(rem)) {
				sb.insert(map.get(rem), "(");
				sb.append(")");
				break;
			}
			map.put(rem, sb.length());
			rem = rem.multiply(BigInteger.TEN);
			sb.append(rem.divide(d));
			if (sb.length() == PRECISION.getPrecision())
				break;
			rem = rem.remainder(d);
		}
		return sb.toString();
	}
	
	/**
	 * Returns this Number as a fractional text-representation.
	 *
	 * Gets used in {@link SystemFunctions}.
	 */
	public String asRational() {
		return num + "/" + denom;
	}
	
	@Override
	public Value as(DataType t) throws NonExpressionException {
		if (t.isArrayType())
			ValueHolder.throwCastingExc(this, t);
		return switch (t.type) {
			case VAR, NR:
				yield this;
			case INT:
				yield new IntValue(num.divide(denom));
			case TEXT:
				String s = fractionToDecimal();
				if (s.matches("(\\d+)\\.((([1-9]+)(0+$))|(0+$))"))
					s = s.replaceAll("(\\.(0+)$|(0+)$)", "");
				yield new TextValue(s);
			default:
				yield ValueHolder.throwCastingExc(this, t);
		};
	}
	
	/** This should only get called in debugging scenarios. */
	@Override
	public String toString() {
		return Output.debugMode ? getClass().getSimpleName() : fractionToDecimal();
	}
	
	// Operations
	
	@Override
	public NumberValue add(NumberValue v) {
		if (v instanceof DecimalValue d) {
			// Fast case: Equals denoms
			if (denom.equals(d.denom))
				return create(num.add(d.num), denom);
			// Common denom, then add
			return create(num.multiply(d.denom).add(d.num.multiply(denom)), denom.multiply(d.denom));
		}
		return v.add(this); // add() in IntVal or ConceptualNrVal
	}
	
	@Override
	public NumberValue sub(NumberValue v) {
		return add(v.negate());
	}
	
	@Override
	public NumberValue mult(NumberValue v) {
		if (v instanceof DecimalValue d)
			return create(num.multiply(d.num), denom.multiply(d.denom));
		return v.mult(this); // mult() in IntVal or ConceptualNrVal
	}
	
	@Override
	public NumberValue div(NumberValue v) {
		if (v.equals(ZERO) || v == NAN || v.isInfinite())
			return NAN;
		if (v instanceof DecimalValue d)
			return mult(create(d.denom, d.num));
		if (v instanceof IntValue i)
			return mult(create(BigInteger.ONE, i.value));
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public NumberValue mod(NumberValue v) {
		if (v.equals(ZERO) || v == NAN || v.isInfinite())
			return NAN;
		if (v instanceof DecimalValue d)
			return create(raw().remainder(d.raw()));
		if (v instanceof IntValue i)
			return create(raw().remainder(new BigDecimal(i.raw())));
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public NumberValue pow(NumberValue v) {
		if (v == NAN || v == NEG_INF)
			return NAN;
		if (v.equals(ZERO))
			return ONE;
		if (v == POS_INF)
			return isPositive() ? POS_INF : NAN;
		if (v instanceof DecimalValue d)
			create(BigDecimalMath.pow(raw(), d.raw(), PRECISION));
		if (v instanceof IntValue i)
			return create(BigDecimalMath.pow(raw(), new BigDecimal(i.value), PRECISION));
		throw new AssertionError("Unimplemented Case.");
	}
	
	/** Calculates the n'th root from v. (this = n) */
	@Override
	public NumberValue root(NumberValue v) {
		if (v == NAN || v.isInfinite())
			return NAN;
		if (v.equals(ZERO))
			return ZERO;
		if (v instanceof DecimalValue d)
			return create(BigDecimalMath.root(d.raw(), raw(), PRECISION));
		if (v instanceof IntValue i)
			return create(BigDecimalMath.root(new BigDecimal(i.value), raw(), PRECISION));
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public BigDecimal raw() {
		return new BigDecimal(num).divide(new BigDecimal(denom), PRECISION);
	}
}
