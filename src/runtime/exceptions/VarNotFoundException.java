package runtime.exceptions;

import building.expressions.abstractions.Scope;

/** Gets thrown by {@link Scope#getVar(String, int)}. */
@SuppressWarnings("serial")
public class VarNotFoundException extends AbstractRuntimeException {

	public VarNotFoundException(int line, String message) {
		super(line, message);
	}

}
