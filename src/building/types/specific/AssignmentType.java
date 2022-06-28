package building.types.specific;

import static building.types.abstractions.SuperType.*;
import static building.types.specific.operators.InfixOpType.*;

import building.types.abstractions.*;
import building.types.specific.operators.*;

public enum AssignmentType implements SpecificType {
	
	NORMAL("=", null), ADDI("+=", ADD), SUBI("-=", SUB), MULTI("*=", MULT), DIVI("/=", DIV), POWI("^=", POW), MODI("%=", MOD);
	
	final String symbol;
	
	/** The corresponding Infix-Operator. */
	public final InfixOpType op;
	
	AssignmentType(String label, InfixOpType op) {
		this.symbol = label;
		this.op = op;
	}
	
	@Override
	public AbstractType[] abstractExpected() {
		return new AbstractType[] {VAL_HOLDER_TYPE};
	}
	
	@Override
	public String toString() {
		return symbol;
	}
}
