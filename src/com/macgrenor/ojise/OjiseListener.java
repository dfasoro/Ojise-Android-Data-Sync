package com.macgrenor.ojise;

public interface OjiseListener {
	abstract void onThreadCompleted(Omose thread);
	abstract void onItemCompleted(Ise item);
	abstract void onBatchComplete(Ojise batch);
}
