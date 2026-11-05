package com.qateralong.nimbus.common;

import java.util.List;

public interface ClassLists<T> {
    List<T> nimbus$all();

    // the live list of instances of the class, built on first use like find()
    <S> List<S> nimbus$of(Class<S> type);
}
