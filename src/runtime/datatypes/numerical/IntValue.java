package runtime.datatypes.numerical;

import static building.types.specific.datatypes.SingleType.*;
import static runtime.datatypes.numerical.ConceptualNrValue.*;

import java.math.*;

import building.expressions.abstractions.interfaces.*;
import building.types.specific.datatypes.*;
import ch.obermuhlner.math.big.*;
import errorhandeling.*;
import runtime.datatypes.*;
import runtime.datatypes.array.*;
import runtime.datatypes.textual.*;

/**
 * An Integer-Value with up to 100 digits.
 *
 * @see DataType#INT
 */
public final class IntValue extends NumberValue {
	
	public final BigInteger value;
	
	/** Produces a {@link IntValue} from a {@link Long}. */
	public IntValue(long value) {
		this(BigInteger.valueOf(value));
	}
	
	/** Produces a {@link IntValue} from a {@link BigInteger}. */
	public IntValue(BigInteger value) {
		super(INT);
		this.value = value;
	}
	
	/** Returns true if this number is even. */
	public final boolean isEven() { return !isOdd(); }
	
	/** Returns true if this number is odd. */
	public final boolean isOdd() { return value.testBit(0); }
	
	@Override
	public Value as(DataType t) throws NonExpressionException {
		if (t.isArrayType()) {
			final String line = value.toString();
			IntValue[] intArray = new IntValue[line.length()];
			for (int i = 0; i < intArray.length; i++)
				intArray[i] = new IntValue(Character.getNumericValue(line.charAt(i)));
			return new ArrayValue(t, intArray);
		}
		return switch (t.type) {
			case VAR, INT, NR -> this;
			case TEXT -> new TextValue(value.toString());
			default -> ValueHolder.throwCastingExc(this, t);
		};
	}
	
	/** This should only get called in debugging scenarios. */
	@Override
	public String toString() {
		return value.toString();
	}
	
	@Override
	public NumberValue add(NumberValue v) {
		if (v == NAN || v.isInfinite())
			return v;
		if (v instanceof DecimalValue d)
			return create(value.multiply(d.denom).add(d.num), d.denom);
		if (v instanceof IntValue i)
			return new IntValue(value.add(i.value));
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public NumberValue sub(NumberValue v) {
		return add(v.negate());
	}
	
	@Override
	public NumberValue mult(NumberValue v) {
		if (v == NAN)
			return v;
		if (v.equals(ZERO))
			return ZERO;
		if (v.isInfinite())
			return isPositive() ? v : v.negate();
		if (v instanceof DecimalValue d)
			return create(value.multiply(d.num), d.denom);
		if (v instanceof IntValue i)
			return new IntValue(value.multiply(i.value));
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public NumberValue div(NumberValue v) {
		if (equals(ZERO))
			return this;
		if (v == NAN || v.isInfinite())
			return NAN;
		if (v instanceof DecimalValue d)
			return mult(create(d.denom, d.num));
		if (v instanceof IntValue i)
			return create(value, i.value);
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public NumberValue mod(NumberValue v) {
		if (v.equals(ZERO) || v == NAN || v.isInfinite())
			return NAN;
		if (v instanceof DecimalValue d)
			return create(new BigDecimal(value).remainder(d.raw()));
		if (v instanceof IntValue i)
			return new IntValue(value.remainder(i.value));
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public NumberValue pow(NumberValue v) {
		if (v == NAN || v == NEG_INF || (equals(ZERO) && v.equals(ZERO)))
			return NAN;
		if (v.equals(ZERO) || equals(ONE))
			return ONE;
		if (equals(ZERO))
			return ZERO;
		if (v == POS_INF)
			return isPositive() ? POS_INF : NAN;
		if (v instanceof DecimalValue d)
			create(BigDecimalMath.pow(new BigDecimal(value), d.raw(), PRECISION));
		if (v instanceof IntValue i)
			return create(BigDecimalMath.pow(new BigDecimal(value), new BigDecimal(i.value), PRECISION));
		throw new AssertionError("Unimplemented Case.");
	}
	
	/**
	 * Calculates the n'th root from v. (this = n)
	 */
	@Override
	public NumberValue root(NumberValue v) {
		if (v == NAN || v.isInfinite())
			return NAN;
		if (v.equals(ZERO))
			return ZERO;
		if (equals(ZERO))
			return v.equals(ONE) ? NAN : POS_INF;
		if (equals(ONE))
			return v;
		if (v instanceof DecimalValue d)
			return create(BigDecimalMath.root(d.raw(), new BigDecimal(value), PRECISION));
		if (v instanceof IntValue i)
			return create(BigDecimalMath.root(new BigDecimal(i.value), new BigDecimal(value), PRECISION));
		throw new AssertionError("Unimplemented Case.");
	}
	
	@Override
	public BigInteger raw() {
		return value;
	}
	
	/** Returns the faculty of this {@link IntValue}. */
	public IntValue fac() {
		BigInteger fac = BigInteger.ONE;
		for (long i = value.longValueExact(); i > 0; i--)
			fac = fac.multiply(BigInteger.valueOf(i));
		return new IntValue(fac);
	}
}
