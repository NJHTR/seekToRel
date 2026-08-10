package com.reportai.recovery;

import java.util.*;

public final class RecoveryStrategyRegistry {
    private final Map<RecoveryAction, RecoveryStrategy> strategies;
    public RecoveryStrategyRegistry(Collection<? extends RecoveryStrategy> strategies) { Map<RecoveryAction,RecoveryStrategy> copy=new EnumMap<>(RecoveryAction.class); for(RecoveryStrategy s:strategies){if(s==null||copy.put(s.action(),s)!=null)throw new IllegalArgumentException("duplicate recovery strategy");} this.strategies=Map.copyOf(copy); }
    public RecoveryStrategyRegistry() { this(List.of()); }
    public Optional<RecoveryStrategy> find(RecoveryAction action){return Optional.ofNullable(strategies.get(action));}
    public RecoveryStrategy require(RecoveryAction action){return find(action).orElseThrow(()->new RecoveryConflictException("no recovery strategy for "+action));}
}
