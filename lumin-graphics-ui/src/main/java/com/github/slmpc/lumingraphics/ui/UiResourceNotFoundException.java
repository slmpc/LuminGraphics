package com.github.slmpc.lumingraphics.ui;
public final class UiResourceNotFoundException extends IllegalArgumentException { private static final long serialVersionUID=1L; public UiResourceNotFoundException(String type,String id){super("Missing UI "+type+": "+id);} }
