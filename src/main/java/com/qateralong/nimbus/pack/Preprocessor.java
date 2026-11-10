package com.qateralong.nimbus.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The conditional part of the C preprocessor for pack files: #if/#ifdef/#ifndef/#elif/#else/#endif with full integer and
// float expressions, object-like #define/#undef. Returns the active lines; directives are dropped.
public final class Preprocessor {
    private Preprocessor() {
    }

    public static String run(String text, Map<String, String> macros) {
        StringBuilder out = new StringBuilder(text.length());
        // per nesting level: 0 = taking this branch, 1 = looking for a true branch, 2 = a branch was taken already
        ArrayList<Integer> stack = new ArrayList<>();
        boolean active = true;
        String[] lines = text.split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            while (line.endsWith("\\") && n + 1 < lines.length && line.trim().startsWith("#")) {
                line = line.substring(0, line.length() - 1) + " " + lines[++n];
            }
            String t = line.trim();
            if (!t.startsWith("#")) {
                if (active) {
                    out.append(line).append('\n');
                }
                continue;
            }
            String directive = t.substring(1).trim();
            String word = directive.split("[\\s(]", 2)[0];
            String rest = directive.substring(word.length()).trim();
            switch (word) {
                case "ifdef", "ifndef", "if" -> {
                    boolean parentActive = active;
                    boolean value = parentActive && (word.equals("if") ? eval(rest, macros) != 0
                            : macros.containsKey(name(rest)) == word.equals("ifdef"));
                    stack.add(!parentActive ? 2 : value ? 0 : 1);
                    active = parentActive && value;
                }
                case "elif" -> {
                    if (stack.isEmpty()) {
                        break;
                    }
                    int state = stack.get(stack.size() - 1);
                    if (state == 1 && parentActive(stack) && eval(rest, macros) != 0) {
                        stack.set(stack.size() - 1, 0);
                        active = true;
                    } else {
                        if (state == 0) {
                            stack.set(stack.size() - 1, 2);
                        }
                        active = false;
                    }
                }
                case "else" -> {
                    if (stack.isEmpty()) {
                        break;
                    }
                    int state = stack.get(stack.size() - 1);
                    if (state == 1 && parentActive(stack)) {
                        stack.set(stack.size() - 1, 0);
                        active = true;
                    } else {
                        stack.set(stack.size() - 1, 2);
                        active = false;
                    }
                }
                case "endif" -> {
                    if (!stack.isEmpty()) {
                        stack.remove(stack.size() - 1);
                    }
                    active = stack.isEmpty() || stack.get(stack.size() - 1) == 0 && parentActive(stack);
                }
                case "define" -> {
                    if (active) {
                        String[] parts = rest.split("\\s+", 2);
                        if (!parts[0].isEmpty() && !parts[0].contains("(")) {
                            macros.put(parts[0], parts.length > 1 ? stripComment(parts[1]).trim() : "");
                        }
                    }
                }
                case "undef" -> {
                    if (active) {
                        macros.remove(name(rest));
                    }
                }
                default -> {
                    if (active) {
                        out.append(line).append('\n');
                    }
                }
            }
        }
        return out.toString();
    }

    private static boolean parentActive(List<Integer> stack) {
        for (int i = 0; i < stack.size() - 1; i++) {
            if (stack.get(i) != 0) {
                return false;
            }
        }
        return true;
    }

    private static String name(String rest) {
        return stripComment(rest).trim().split("\\s+")[0];
    }

    static String stripComment(String s) {
        int c = s.indexOf("//");
        if (c >= 0) {
            s = s.substring(0, c);
        }
        int b = s.indexOf("/*");
        if (b >= 0) {
            s = s.substring(0, b);
        }
        return s;
    }

    public static double eval(String expression, Map<String, String> macros) {
        try {
            return new Parser(stripComment(expression), macros, 0).parse();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static final class Parser {
        private final String s;
        private final Map<String, String> macros;
        private final int depth;
        private int i;

        Parser(String s, Map<String, String> macros, int depth) {
            this.s = s;
            this.macros = macros;
            this.depth = depth;
        }

        double parse() {
            double v = this.ternary();
            return v;
        }

        private void skip() {
            while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
                this.i++;
            }
        }

        private boolean eat(String op) {
            this.skip();
            if (this.s.startsWith(op, this.i)) {
                // do not take "<" from "<<", "&" from "&&" and so on
                if (op.length() == 1 && this.i + 1 < this.s.length()) {
                    char next = this.s.charAt(this.i + 1);
                    char c = op.charAt(0);
                    if ((c == '<' || c == '>' || c == '=' || c == '!') && next == '=' || (c == '&' || c == '|' || c == '<' || c == '>') && next == c) {
                        return false;
                    }
                }
                this.i += op.length();
                return true;
            }
            return false;
        }

        private double ternary() {
            double c = this.or();
            if (this.eat("?")) {
                double a = this.ternary();
                this.eat(":");
                double b = this.ternary();
                return c != 0 ? a : b;
            }
            return c;
        }

        private double or() {
            double v = this.and();
            while (this.eat("||")) {
                double r = this.and();
                v = v != 0 || r != 0 ? 1 : 0;
            }
            return v;
        }

        private double and() {
            double v = this.bitOr();
            while (this.eat("&&")) {
                double r = this.bitOr();
                v = v != 0 && r != 0 ? 1 : 0;
            }
            return v;
        }

        private double bitOr() {
            double v = this.bitXor();
            while (this.eat("|")) {
                v = (long) v | (long) this.bitXor();
            }
            return v;
        }

        private double bitXor() {
            double v = this.bitAnd();
            while (this.eat("^")) {
                v = (long) v ^ (long) this.bitAnd();
            }
            return v;
        }

        private double bitAnd() {
            double v = this.equality();
            while (this.eat("&")) {
                v = (long) v & (long) this.equality();
            }
            return v;
        }

        private double equality() {
            double v = this.relation();
            while (true) {
                if (this.eat("==")) {
                    v = v == this.relation() ? 1 : 0;
                } else if (this.eat("!=")) {
                    v = v != this.relation() ? 1 : 0;
                } else {
                    return v;
                }
            }
        }

        private double relation() {
            double v = this.shift();
            while (true) {
                if (this.eat("<=")) {
                    v = v <= this.shift() ? 1 : 0;
                } else if (this.eat(">=")) {
                    v = v >= this.shift() ? 1 : 0;
                } else if (this.eat("<")) {
                    v = v < this.shift() ? 1 : 0;
                } else if (this.eat(">")) {
                    v = v > this.shift() ? 1 : 0;
                } else {
                    return v;
                }
            }
        }

        private double shift() {
            double v = this.sum();
            while (true) {
                if (this.eat("<<")) {
                    v = (long) v << (long) this.sum();
                } else if (this.eat(">>")) {
                    v = (long) v >> (long) this.sum();
                } else {
                    return v;
                }
            }
        }

        private double sum() {
            double v = this.product();
            while (true) {
                if (this.eat("+")) {
                    v += this.product();
                } else if (this.eat("-")) {
                    v -= this.product();
                } else {
                    return v;
                }
            }
        }

        private double product() {
            double v = this.unary();
            while (true) {
                if (this.eat("*")) {
                    v *= this.unary();
                } else if (this.eat("/")) {
                    double r = this.unary();
                    v = r == 0 ? 0 : (v == Math.rint(v) && r == Math.rint(r) ? (long) v / (long) r : v / r);
                } else if (this.eat("%")) {
                    double r = this.unary();
                    v = r == 0 ? 0 : v % r;
                } else {
                    return v;
                }
            }
        }

        private double unary() {
            if (this.eat("!")) {
                return this.unary() == 0 ? 1 : 0;
            }
            if (this.eat("-")) {
                return -this.unary();
            }
            if (this.eat("+")) {
                return this.unary();
            }
            if (this.eat("~")) {
                return ~(long) this.unary();
            }
            return this.primary();
        }

        private double primary() {
            this.skip();
            if (this.eat("(")) {
                double v = this.ternary();
                this.eat(")");
                return v;
            }
            int start = this.i;
            if (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
                while (this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
                    this.i++;
                }
                String number = this.s.substring(start, this.i).toLowerCase(java.util.Locale.ROOT);
                if (number.startsWith("0x")) {
                    return Long.parseLong(number.substring(2).replaceAll("[ul]+$", ""), 16);
                }
                return Double.parseDouble(number.replaceAll("[ulf]+$", ""));
            }
            while (this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '_')) {
                this.i++;
            }
            String id = this.s.substring(start, this.i);
            if (id.isEmpty()) {
                throw new IllegalStateException("bad expression");
            }
            if (id.equals("defined")) {
                boolean paren = this.eat("(");
                this.skip();
                int at = this.i;
                while (this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '_')) {
                    this.i++;
                }
                String name = this.s.substring(at, this.i);
                if (paren) {
                    this.eat(")");
                }
                return this.macros.containsKey(name) ? 1 : 0;
            }
            if (id.equals("true")) {
                return 1;
            }
            if (id.equals("false")) {
                return 0;
            }
            String value = this.macros.get(id);
            if (value == null || this.depth > 16) {
                return 0;
            }
            if (value.isEmpty()) {
                // a switched-on boolean option
                return 1;
            }
            return new Parser(value, this.macros, this.depth + 1).parse();
        }
    }
}
