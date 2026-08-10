package com.reportai.recovery;

import java.util.List;
import java.util.Objects;

final class PayloadRules {
    private PayloadRules() { }
    static String text(String value, String field, int max) { Objects.requireNonNull(value, field); String n=value.strip(); if(n.isBlank()||n.length()>max||n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field+" is invalid"); return n; }
    static List<String> list(List<String> value, String field, int max) { List<String> result=value==null?List.of():List.copyOf(value); if(result.size()>max||result.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException(field+" is invalid"); return result; }
}
