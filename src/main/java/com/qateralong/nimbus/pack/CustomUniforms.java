package com.qateralong.nimbus.pack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

// OptiFine custom uniforms and variables from shaders.properties (uniform.<type>.<name> / variable.<type>.<name> = expr):
// parsed once, evaluated every frame in declaration order against the built-in values.
public final class CustomUniforms {
    interface Node {
        double[] eval(CustomUniforms ctx);
    }

    public record Entry(String type, String name, boolean uniform, Node node) {
    }

    public final List<Entry> entries = new ArrayList<>();
    public final Map<String, double[]> values = new HashMap<>();
    private final Map<Integer, Double> smooth = new HashMap<>();
    private int autoSmooth = 100000;
    public double frameTime;
    private final java.util.Random random = new java.util.Random();

    public static CustomUniforms parse(Map<String, String> properties) {
        CustomUniforms c = new CustomUniforms();
        for (var e : properties.entrySet()) {
            String key = e.getKey();
            boolean uniform = key.startsWith("uniform.");
            if (!uniform && !key.startsWith("variable.")) {
                continue;
            }
            String[] parts = key.split("\\.", 3);
            if (parts.length < 3) {
                continue;
            }
            try {
                c.entries.add(new Entry(parts[1], parts[2], uniform, new Parser(e.getValue(), c).parse()));
            } catch (RuntimeException ex) {
                PackProgram.warn("custom uniform " + parts[2], ex);
            }
        }
        return c;
    }

    // values: built-ins for this frame (vectors as arrays)
    public void evaluate(Map<String, double[]> builtins, double frameTime) {
        this.frameTime = frameTime;
        this.values.putAll(builtins);
        for (Entry e : this.entries) {
            double[] v;
            try {
                v = e.node.eval(this);
            } catch (RuntimeException ex) {
                v = new double[] {0};
            }
            this.values.put(e.name, v);
        }
    }

    double[] lookup(String name) {
        double[] v = this.values.get(name);
        return v == null ? new double[] {0} : v;
    }

    double smooth(int id, double value, double up, double down) {
        Double previous = this.smooth.get(id);
        if (previous == null) {
            this.smooth.put(id, value);
            return value;
        }
        double fade = value > previous ? up : down;
        double next = fade <= 0 ? value : value + (previous - value) * Math.pow(0.01, this.frameTime / fade);
        this.smooth.put(id, next);
        return next;
    }

    int nextSmoothId() {
        return this.autoSmooth++;
    }

    static double[] num(double v) {
        return new double[] {v};
    }

    private static final class Parser {
        private final String s;
        private final CustomUniforms owner;
        private int i;

        Parser(String s, CustomUniforms owner) {
            this.s = s;
            this.owner = owner;
        }

        Node parse() {
            Node n = this.or();
            this.skip();
            if (this.i < this.s.length()) {
                throw new IllegalStateException("unexpected '" + this.s.substring(this.i) + "'");
            }
            return n;
        }

        private void skip() {
            while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
                this.i++;
            }
        }

        private boolean eat(String op) {
            this.skip();
            if (this.s.startsWith(op, this.i)) {
                if (op.length() == 1 && this.i + 1 < this.s.length()) {
                    char n = this.s.charAt(this.i + 1);
                    char c = op.charAt(0);
                    if ((c == '<' || c == '>' || c == '=' || c == '!') && n == '=' || (c == '&' || c == '|') && n == c) {
                        return false;
                    }
                }
                this.i += op.length();
                return true;
            }
            return false;
        }

        private Node binary(Node a, Node b, java.util.function.DoubleBinaryOperator op) {
            return ctx -> {
                double[] x = a.eval(ctx);
                double[] y = b.eval(ctx);
                int n = Math.max(x.length, y.length);
                double[] r = new double[n];
                for (int k = 0; k < n; k++) {
                    r[k] = op.applyAsDouble(x[Math.min(k, x.length - 1)], y[Math.min(k, y.length - 1)]);
                }
                return r;
            };
        }

        private Node or() {
            Node n = this.and();
            while (this.eat("||")) {
                Node l = n;
                Node r = this.and();
                n = ctx -> num(l.eval(ctx)[0] != 0 || r.eval(ctx)[0] != 0 ? 1 : 0);
            }
            return n;
        }

        private Node and() {
            Node n = this.compare();
            while (this.eat("&&")) {
                Node l = n;
                Node r = this.compare();
                n = ctx -> num(l.eval(ctx)[0] != 0 && r.eval(ctx)[0] != 0 ? 1 : 0);
            }
            return n;
        }

        private Node compare() {
            Node n = this.sum();
            while (true) {
                Node l = n;
                if (this.eat("==")) {
                    Node r = this.sum();
                    n = ctx -> num(l.eval(ctx)[0] == r.eval(ctx)[0] ? 1 : 0);
                } else if (this.eat("!=")) {
                    Node r = this.sum();
                    n = ctx -> num(l.eval(ctx)[0] != r.eval(ctx)[0] ? 1 : 0);
                } else if (this.eat("<=")) {
                    Node r = this.sum();
                    n = ctx -> num(l.eval(ctx)[0] <= r.eval(ctx)[0] ? 1 : 0);
                } else if (this.eat(">=")) {
                    Node r = this.sum();
                    n = ctx -> num(l.eval(ctx)[0] >= r.eval(ctx)[0] ? 1 : 0);
                } else if (this.eat("<")) {
                    Node r = this.sum();
                    n = ctx -> num(l.eval(ctx)[0] < r.eval(ctx)[0] ? 1 : 0);
                } else if (this.eat(">")) {
                    Node r = this.sum();
                    n = ctx -> num(l.eval(ctx)[0] > r.eval(ctx)[0] ? 1 : 0);
                } else {
                    return n;
                }
            }
        }

        private Node sum() {
            Node n = this.product();
            while (true) {
                if (this.eat("+")) {
                    n = this.binary(n, this.product(), Double::sum);
                } else if (this.eat("-")) {
                    n = this.binary(n, this.product(), (a, b) -> a - b);
                } else {
                    return n;
                }
            }
        }

        private Node product() {
            Node n = this.unary();
            while (true) {
                if (this.eat("*")) {
                    n = this.binary(n, this.unary(), (a, b) -> a * b);
                } else if (this.eat("/")) {
                    n = this.binary(n, this.unary(), (a, b) -> b == 0 ? 0 : a / b);
                } else if (this.eat("%")) {
                    n = this.binary(n, this.unary(), (a, b) -> b == 0 ? 0 : a % b);
                } else {
                    return n;
                }
            }
        }

        private Node unary() {
            if (this.eat("!")) {
                Node n = this.unary();
                return ctx -> num(n.eval(ctx)[0] == 0 ? 1 : 0);
            }
            if (this.eat("-")) {
                Node n = this.unary();
                return ctx -> {
                    double[] v = n.eval(ctx).clone();
                    for (int k = 0; k < v.length; k++) {
                        v[k] = -v[k];
                    }
                    return v;
                };
            }
            if (this.eat("+")) {
                return this.unary();
            }
            return this.primary();
        }

        private Node primary() {
            this.skip();
            if (this.eat("(")) {
                Node n = this.or();
                this.eat(")");
                return this.member(n);
            }
            int start = this.i;
            if (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
                while (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.' || this.s.charAt(this.i) == 'e'
                        || this.s.charAt(this.i) == 'E' || (this.s.charAt(this.i) == '-' || this.s.charAt(this.i) == '+') && (this.s.charAt(this.i - 1) == 'e'
                        || this.s.charAt(this.i - 1) == 'E'))) {
                    this.i++;
                }
                double v = Double.parseDouble(this.s.substring(start, this.i).replaceAll("[fF]$", ""));
                if (this.i < this.s.length() && (this.s.charAt(this.i) == 'f' || this.s.charAt(this.i) == 'F')) {
                    this.i++;
                }
                double[] c = num(v);
                return ctx -> c;
            }
            while (this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '_')) {
                this.i++;
            }
            String id = this.s.substring(start, this.i);
            if (id.isEmpty()) {
                throw new IllegalStateException("bad expression at " + this.i);
            }
            if (this.eat("(")) {
                List<Node> args = new ArrayList<>();
                if (!this.eat(")")) {
                    do {
                        args.add(this.or());
                    } while (this.eat(","));
                    this.eat(")");
                }
                return this.member(this.function(id, args));
            }
            if (id.equals("true")) {
                return ctx -> num(1);
            }
            if (id.equals("false")) {
                return ctx -> num(0);
            }
            double[] constant = Constants.get(id);
            if (constant != null) {
                return ctx -> constant;
            }
            return this.member(ctx -> ctx.lookup(id));
        }

        private Node member(Node n) {
            // matrix element: name.<column>.<row>
            if (this.s.startsWith(".", this.i) && this.i + 3 < this.s.length() && Character.isDigit(this.s.charAt(this.i + 1))
                    && this.s.charAt(this.i + 2) == '.' && Character.isDigit(this.s.charAt(this.i + 3))) {
                int index = (this.s.charAt(this.i + 1) - '0') * 4 + (this.s.charAt(this.i + 3) - '0');
                this.i += 4;
                return ctx -> {
                    double[] m = n.eval(ctx);
                    return num(index < m.length ? m[index] : 0);
                };
            }
            if (this.s.startsWith(".", this.i) && this.i + 1 < this.s.length() && Character.isLetter(this.s.charAt(this.i + 1))) {
                this.i++;
                int start = this.i;
                while (this.i < this.s.length() && Character.isLetter(this.s.charAt(this.i))) {
                    this.i++;
                }
                String swizzle = this.s.substring(start, this.i);
                return ctx -> {
                    double[] v = n.eval(ctx);
                    double[] r = new double[swizzle.length()];
                    for (int k = 0; k < swizzle.length(); k++) {
                        int c = "xyzwrgba".indexOf(swizzle.charAt(k)) % 4;
                        r[k] = v[Math.min(Math.max(c, 0), v.length - 1)];
                    }
                    return r;
                };
            }
            return n;
        }

        private Node function(String name, List<Node> a) {
            Function<Function<Double, Double>, Node> f1 = fn -> ctx -> {
                double[] v = a.get(0).eval(ctx).clone();
                for (int k = 0; k < v.length; k++) {
                    v[k] = fn.apply(v[k]);
                }
                return v;
            };
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "sin" -> f1.apply(Math::sin);
                case "cos" -> f1.apply(Math::cos);
                case "tan" -> f1.apply(Math::tan);
                case "asin" -> f1.apply(Math::asin);
                case "acos" -> f1.apply(Math::acos);
                case "atan" -> f1.apply(Math::atan);
                case "atan2" -> this.binary(a.get(0), a.get(1), Math::atan2);
                case "torad" -> f1.apply(Math::toRadians);
                case "todeg" -> f1.apply(Math::toDegrees);
                case "abs" -> f1.apply(Math::abs);
                case "floor" -> f1.apply(Math::floor);
                case "ceil" -> f1.apply(Math::ceil);
                case "round" -> f1.apply(x -> (double) Math.round(x));
                case "frac" -> f1.apply(x -> x - Math.floor(x));
                case "exp" -> f1.apply(Math::exp);
                case "log" -> f1.apply(Math::log);
                case "sqrt" -> f1.apply(Math::sqrt);
                case "signum" -> f1.apply(Math::signum);
                case "pow" -> this.binary(a.get(0), a.get(1), Math::pow);
                case "fmod" -> this.binary(a.get(0), a.get(1), (x, y) -> y == 0 ? 0 : x - y * Math.floor(x / y));
                case "min" -> ctx -> {
                    double[] r = a.get(0).eval(ctx).clone();
                    for (int k = 1; k < a.size(); k++) {
                        double[] v = a.get(k).eval(ctx);
                        for (int c = 0; c < r.length; c++) {
                            r[c] = Math.min(r[c], v[Math.min(c, v.length - 1)]);
                        }
                    }
                    return r;
                };
                case "max" -> ctx -> {
                    double[] r = a.get(0).eval(ctx).clone();
                    for (int k = 1; k < a.size(); k++) {
                        double[] v = a.get(k).eval(ctx);
                        for (int c = 0; c < r.length; c++) {
                            r[c] = Math.max(r[c], v[Math.min(c, v.length - 1)]);
                        }
                    }
                    return r;
                };
                case "clamp" -> ctx -> {
                    double[] r = a.get(0).eval(ctx).clone();
                    double lo = a.get(1).eval(ctx)[0];
                    double hi = a.get(2).eval(ctx)[0];
                    for (int c = 0; c < r.length; c++) {
                        r[c] = Math.max(lo, Math.min(hi, r[c]));
                    }
                    return r;
                };
                case "between" -> ctx -> {
                    double x = a.get(0).eval(ctx)[0];
                    return num(x >= a.get(1).eval(ctx)[0] && x <= a.get(2).eval(ctx)[0] ? 1 : 0);
                };
                case "equals" -> ctx -> num(Math.abs(a.get(0).eval(ctx)[0] - a.get(1).eval(ctx)[0]) <= (a.size() > 2 ? a.get(2).eval(ctx)[0] : 0) ? 1 : 0);
                case "in" -> ctx -> {
                    double x = a.get(0).eval(ctx)[0];
                    for (int k = 1; k < a.size(); k++) {
                        if (a.get(k).eval(ctx)[0] == x) {
                            return num(1);
                        }
                    }
                    return num(0);
                };
                case "if" -> ctx -> {
                    for (int k = 0; k + 1 < a.size(); k += 2) {
                        if (a.get(k).eval(ctx)[0] != 0) {
                            return a.get(k + 1).eval(ctx);
                        }
                    }
                    return a.size() % 2 == 1 ? a.get(a.size() - 1).eval(ctx) : num(0);
                };
                case "smooth" -> {
                    // smooth([id,] value, [fadeUp, [fadeDown]]), times in seconds; state per call site
                    int id = this.owner.nextSmoothId();
                    yield ctx -> {
                        int n = a.size();
                        boolean withId = n >= 4;
                        Node value = a.get(withId ? 1 : 0);
                        double up = n > (withId ? 2 : 1) ? a.get(withId ? 2 : 1).eval(ctx)[0] : 1.0;
                        double down = n > (withId ? 3 : 2) ? a.get(withId ? 3 : 2).eval(ctx)[0] : up;
                        return num(ctx.smooth(id, value.eval(ctx)[0], up, down));
                    };
                }
                case "random" -> ctx -> num(ctx.random.nextDouble());
                case "vec2" -> ctx -> new double[] {a.get(0).eval(ctx)[0], a.get(1).eval(ctx)[0]};
                case "vec3" -> ctx -> new double[] {a.get(0).eval(ctx)[0], a.get(1).eval(ctx)[0], a.get(2).eval(ctx)[0]};
                case "vec4" -> ctx -> new double[] {a.get(0).eval(ctx)[0], a.get(1).eval(ctx)[0], a.get(2).eval(ctx)[0], a.get(3).eval(ctx)[0]};
                default -> ctx -> num(0);
            };
        }
    }

    // BIOME_*, PPT_* and CAT_* names
    static final class Constants {
        static final Map<String, double[]> MAP = new HashMap<>();

        static double[] get(String id) {
            double[] v = MAP.get(id);
            if (v == null && (id.startsWith("BIOME_") || id.startsWith("CAT_"))) {
                return new double[] {-1};
            }
            return v;
        }

        static {
            MAP.put("pi", num(Math.PI));
            MAP.put("PPT_NONE", num(0));
            MAP.put("PPT_RAIN", num(1));
            MAP.put("PPT_SNOW", num(2));
        }
    }
}
