package com.duskafka.archfixtures.time;

import java.time.InstantSource;

/** InstantSource.system(): 걸려야 한다. */
public class CallsInstantSourceSystem {

	public InstantSource source() {
		return InstantSource.system();
	}

}
