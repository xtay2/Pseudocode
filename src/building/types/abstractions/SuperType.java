package building.types.abstractions;

import static building.types.specific.BuilderType.*;

import building.types.specific.*;
import building.types.specific.datatypes.*;
import building.types.specific.operators.*;

public enum SuperType implements UnspecificType {
	
	// Specific Types
	DYNAMIC_TYPE(DynamicType.values()),
	
	FLAG_TYPE(FlagType.values()),
	
	KEYWORD_TYPE(KeywordType.values()),
	
	BUILDER_TYPE(BuilderType.values()),
	
	PREFIX_OP_TYPE(PrefixOpType.values()),
	
	INFIX_OP_TYPE(InfixOpType.values()),
	
	POSTFIX_OP_TYPE(PostfixOpType.values()),
	
	ASSIGNMENT_TYPE(AssignmentType.values()),
	
	DATA_TYPE(SingleType.values()),
	
	BLUEPRINT_TYPE(BlueprintType.values()),
	
	// Groups
	
	VAL_HOLDER_TYPE(ARRAY_START, OPEN_BRACKET, DATA_TYPE, DYNAMIC_TYPE, PREFIX_OP_TYPE, MULTI_CALL_START),
	
	AFTER_VALUE_TYPE(
			INFIX_OP_TYPE,
			POSTFIX_OP_TYPE,
			KEYWORD_TYPE,
			COMMA,
			CLOSE_BRACKET,
			ARRAY_END,
			OPEN_BLOCK,
			TO,
			STEP,
			AS,
			MULTI_CALL_END),
	
	START_OF_LINE_TYPE(KEYWORD_TYPE, VAL_HOLDER_TYPE, FLAG_TYPE, CLOSE_BLOCK, BLUEPRINT_TYPE);
	
	private final AbstractType[] subValues;
	
	SuperType(AbstractType... subValues) {
		this.subValues = subValues;
	}
	
	@Override
	public AbstractType[] directSubValues() {
		return subValues;
	}
}
