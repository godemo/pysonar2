package org.yinwang.pysonar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.pysonar.ast.*;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class RubyParser {
    @Nullable
    Process rubyProcess;
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String RUBY_EXE = "irb";
    private static final String dumpPythonResource = "org/yinwang/pysonar/ruby/dump_ruby.rb";
    private String exchangeFile;
    private String endMark;
    private String rbStub;

    private static final int TIMEOUT = 5000;


    public RubyParser() {
        String tmpDir = _.getSystemTempDir();
        String sid = _.newSessionId();

        exchangeFile = _.makePathString(tmpDir, "pysonar2", "json." + sid);
        endMark = _.makePathString(tmpDir, "pysonar2", "end." + sid);
        rbStub = _.makePathString(tmpDir, "pysonar2", "dump_ruby." + sid);

        startRubyProcesses();

        if (rubyProcess != null) {
            _.msg("Started: " + RUBY_EXE);
        }
    }


    // start or restart ruby process
    private void startRubyProcesses() {
        if (rubyProcess != null) {
            rubyProcess.destroy();
        }

        rubyProcess = startInterpreter(RUBY_EXE);

        if (rubyProcess == null) {
            _.die("You don't seem to have ruby on PATH");
        }
    }


    public void close() {
        new File(rbStub).delete();
        new File(exchangeFile).delete();
        new File(endMark).delete();
    }


    public Map<String, Object> deserialize(String text) {
        return gson.fromJson(text, Map.class);
    }


    @Nullable
    private Block convertBlock(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            return new Block(convertList(o), 0, 0);
        }
    }


    @Nullable
    private List<Node> convertList(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            List<Map<String, Object>> in = (List<Map<String, Object>>) o;
            List<Node> out = new ArrayList<>();

            for (Map<String, Object> m : in) {
                Node n = deJson(m);
                if (n != null) {
                    out.add(n);
                }
            }

            return out;
        }
    }


    @Nullable
    private List<Op> convertListOp(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            List<Map<String, Object>> in = (List<Map<String, Object>>) o;
            List<Op> out = new ArrayList<>();

            for (Map<String, Object> m : in) {
                Op n = convertOp(m);
                if (n != null) {
                    out.add(n);
                }
            }

            return out;
        }
    }


    @Nullable
    private List<Keyword> convertListKeyword(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            List<Map<String, Object>> in = (List<Map<String, Object>>) o;
            List<Keyword> out = new ArrayList<Keyword>();

            for (Map<String, Object> m : in) {
                Node n = deJson(m);
                if (n != null) {
                    out.add((Keyword) n);
                }
            }

            return out;
        }
    }


    @Nullable
    private List<ExceptHandler> convertListExceptHandler(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            List<Map<String, Object>> in = (List<Map<String, Object>>) o;
            List<ExceptHandler> out = new ArrayList<ExceptHandler>();

            for (Map<String, Object> m : in) {
                Node n = deJson(m);
                if (n != null) {
                    out.add((ExceptHandler) n);
                }
            }

            return out;
        }
    }


    @Nullable
    private List<Alias> convertListAlias(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            List<Map<String, Object>> in = (List<Map<String, Object>>) o;
            List<Alias> out = new ArrayList<Alias>();

            for (Map<String, Object> m : in) {
                Node n = deJson(m);
                if (n != null) {
                    out.add((Alias) n);
                }
            }

            return out;
        }
    }


    @Nullable
    private List<Comprehension> convertListComprehension(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            List<Map<String, Object>> in = (List<Map<String, Object>>) o;
            List<Comprehension> out = new ArrayList<Comprehension>();

            for (Map<String, Object> m : in) {
                Node n = deJson(m);
                if (n != null) {
                    out.add((Comprehension) n);
                }
            }

            return out;
        }
    }


    @NotNull
    List<Name> segmentQname(@NotNull String qname, int start, boolean hasLoc) {
        List<Name> result = new ArrayList<>();

        for (int i = 0; i < qname.length(); i++) {
            String name = "";
            while (Character.isSpaceChar(qname.charAt(i))) {
                i++;
            }
            int nameStart = i;

            while (i < qname.length() &&
                    (Character.isJavaIdentifierPart(qname.charAt(i)) ||
                            qname.charAt(i) == '*') &&
                    qname.charAt(i) != '.') {
                name += qname.charAt(i);
                i++;
            }

            int nameStop = i;
            int nstart = hasLoc ? start + nameStart : -1;
            int nstop = hasLoc ? start + nameStop : -1;
            result.add(new Name(name, nstart, nstop));
        }

        return result;
    }


    @Nullable
    public Node deJson(Object o) {
        if (!(o instanceof Map)) {
            return null;
        }

        Map<String, Object> map = (Map<String, Object>) o;

        String type = (String) map.get("type");
        Double startDouble = (Double) map.get("start");
        Double endDouble = (Double) map.get("end");

        int start = startDouble == null ? 0 : startDouble.intValue();
        int end = endDouble == null ? 1 : endDouble.intValue();


        if (type.equals("alias")) {         // lower case alias
            String qname = (String) map.get("name");
            List<Name> names = segmentQname(qname, start + "import ".length(), false);
            Name asname = map.get("asname") == null ? null : new Name((String) map.get("asname"));
            return new Alias(names, asname, start, end);
        }

        if (type.equals("Assert")) {
            Node test = deJson(map.get("test"));
            Node msg = deJson(map.get("msg"));
            return new Assert(test, msg, start, end);
        }

        if (type.equals("Attribute")) {
            Node value = deJson(map.get("value"));
            Name attr = (Name) deJson(map.get("attr_name"));
            if (attr == null) {
                attr = new Name((String) map.get("attr"));
            }
            return new Attribute(value, attr, start, end);
        }

        if (type.equals("AugAssign")) {
            Node target = deJson(map.get("target"));
            Node value = deJson(map.get("value"));
            Op op = convertOp(map.get("op"));
            Node operation = new BinOp(op, target, value, target.start, value.end);
            return new Assign(target, operation, start, end);
        }

        if (type.equals("BinOp")) {
            Node left = deJson(map.get("left"));
            Node right = deJson(map.get("right"));
            Op op = convertOp(map.get("op"));

            // compositional operators
            if (op == Op.NotEqual) {
                Node eq = new BinOp(Op.Equal, left, right, start, end);
                return new UnaryOp(Op.Not, eq, start, end);
            }

            if (op == Op.LtE) {
                Node lt = new BinOp(Op.Lt, left, right, start, end);
                Node eq = new BinOp(Op.Eq, left, right, start, end);
                return new BinOp(Op.Or, lt, eq, start, end);
            }

            if (op == Op.GtE) {
                Node gt = new BinOp(Op.Gt, left, right, start, end);
                Node eq = new BinOp(Op.Eq, left, right, start, end);
                return new BinOp(Op.Or, gt, eq, start, end);
            }

            if (op == Op.NotIn) {
                Node in = new BinOp(Op.In, left, right, start, end);
                return new UnaryOp(Op.Not, in, start, end);
            }

            if (op == Op.NotEq) {
                Node in = new BinOp(Op.Eq, left, right, start, end);
                return new UnaryOp(Op.Not, in, start, end);
            }

            return new BinOp(op, left, right, start, end);

        }

        if (type.equals("BoolOp")) {
            List<Node> values = convertList(map.get("values"));
            if (values == null || values.size() < 2) {
                _.die("impossible number of arguments, please fix the Python parser");
            }
            Op op = convertOp(map.get("op"));
            BinOp ret = new BinOp(op, values.get(0), values.get(1), start, end);
            for (int i = 2; i < values.size(); i++) {
                ret = new BinOp(op, ret, values.get(i), start, end);
            }
            return ret;
        }

        if (type.equals("Break")) {
            return new Break(start, end);
        }


        if (type.equals("Bytes")) {
            Object s = map.get("s");
            return new Bytes(s, start, end);
        }


        if (type.equals("Call")) {
            Node func = deJson(map.get("func"));
            List<Node> args = convertList(map.get("args"));
            List<Keyword> keywords = convertListKeyword(map.get("keywords"));
            Node kwargs = deJson(map.get("kwarg"));
            Node starargs = deJson(map.get("starargs"));
            return new Call(func, args, keywords, kwargs, starargs, start, end);
        }

        if (type.equals("ClassDef")) {
            Name name = (Name) deJson(map.get("name_node"));      // hack
            List<Node> bases = convertList(map.get("bases"));
            Block body = convertBlock(map.get("body"));
            return new ClassDef(name, bases, body, start, end);
        }

        // left-fold Compare into
        if (type.equals("Compare")) {
            Node left = deJson(map.get("left"));
            List<Op> ops = convertListOp(map.get("ops"));
            List<Node> comparators = convertList(map.get("comparators"));
            Node result = new BinOp(ops.get(0), left, comparators.get(0), start, end);
            for (int i = 1; i < comparators.size(); i++) {
                Node compNext = new BinOp(ops.get(i), comparators.get(i - 1), comparators.get(i), start, end);
                result = new BinOp(Op.And, result, compNext, start, end);
            }
            return result;
        }

        if (type.equals("comprehension")) {
            Node target = deJson(map.get("target"));
            Node iter = deJson(map.get("iter"));
            List<Node> ifs = convertList(map.get("ifs"));
            return new Comprehension(target, iter, ifs, start, end);
        }

        if (type.equals("Continue")) {
            return new Continue(start, end);
        }

        if (type.equals("Delete")) {
            List<Node> targets = convertList(map.get("targets"));
            return new Delete(targets, start, end);
        }

        if (type.equals("Dict")) {
            List<Node> keys = convertList(map.get("keys"));
            List<Node> values = convertList(map.get("values"));
            return new Dict(keys, values, start, end);
        }

        if (type.equals("DictComp")) {
            Node key = deJson(map.get("key"));
            Node value = deJson(map.get("value"));
            List<Comprehension> generators = convertListComprehension(map.get("generators"));
            return new DictComp(key, value, generators, start, end);
        }

        if (type.equals("Ellipsis")) {
            return new Ellipsis(start, end);
        }

        if (type.equals("ExceptHandler")) {
            Node name = deJson(map.get("name"));
            Node exceptionType = deJson(map.get("type"));
            Block body = convertBlock(map.get("body"));
            return new ExceptHandler(name, exceptionType, body, start, end);
        }

        if (type.equals("Exec")) {
            Node body = deJson(map.get("body"));
            Node globals = deJson(map.get("globals"));
            Node locals = deJson(map.get("locals"));
            return new Exec(body, globals, locals, start, end);
        }

        if (type.equals("Expr")) {
            Node value = deJson(map.get("value"));
            return new Expr(value, start, end);
        }

        if (type.equals("For")) {
            Node target = deJson(map.get("target"));
            Node iter = deJson(map.get("iter"));
            Block body = convertBlock(map.get("body"));
            Block orelse = convertBlock(map.get("orelse"));
            return new For(target, iter, body, orelse, start, end);
        }

        if (type.equals("FunctionDef")) {
            Name name = (Name) deJson(map.get("name_node"));
            Map<String, Object> argsMap = (Map<String, Object>) map.get("args");
            List<Node> args = convertList(argsMap.get("args"));
            List<Node> defaults = convertList(argsMap.get("defaults"));
            Block body = convertBlock(map.get("body"));
            Name vararg = argsMap.get("vararg") == null ? null : new Name((String) argsMap.get("vararg"));
            Name kwarg = argsMap.get("kwarg") == null ? null : new Name((String) argsMap.get("kwarg"));
            return new FunctionDef(name, args, body, defaults, vararg, kwarg, start, end);
        }

        if (type.equals("GeneratorExp")) {
            Node elt = deJson(map.get("elt"));
            List<Comprehension> generators = convertListComprehension(map.get("generators"));
            return new GeneratorExp(elt, generators, start, end);
        }

        if (type.equals("Global")) {
            List<String> names = (List<String>) map.get("names");
            List<Name> nameNodes = new ArrayList<>();
            for (String name : names) {
                nameNodes.add(new Name(name));
            }
            return new Global(nameNodes, start, end);
        }

        if (type.equals("Nonlocal")) {
            List<String> names = (List<String>) map.get("names");
            List<Name> nameNodes = new ArrayList<>();
            for (String name : names) {
                nameNodes.add(new Name(name));
            }
            return new Global(nameNodes, start, end);
        }

        if (type.equals("If")) {
            Node test = deJson(map.get("test"));
            Block body = convertBlock(map.get("body"));
            Block orelse = convertBlock(map.get("orelse"));
            return new If(test, body, orelse, start, end);
        }

        if (type.equals("IfExp")) {
            Node test = deJson(map.get("test"));
            Node body = deJson(map.get("body"));
            Node orelse = deJson(map.get("orelse"));
            return new IfExp(test, body, orelse, start, end);
        }


        if (type.equals("Import")) {
            List<Alias> aliases = convertListAlias(map.get("names"));
            return new Import(aliases, start, end);
        }

        if (type.equals("ImportFrom")) {
            String module = (String) map.get("module");
            List<Name> moduleSeg = module == null ? null : segmentQname(module, start + "from ".length(), true);
            List<Alias> names = convertListAlias(map.get("names"));
            int level = ((Double) map.get("level")).intValue();
            return new ImportFrom(moduleSeg, names, level, start, end);
        }

        if (type.equals("Index")) {
            Node value = deJson(map.get("value"));
            return new Index(value, start, end);
        }

        if (type.equals("keyword")) {
            String arg = (String) map.get("arg");
            Node value = deJson(map.get("value"));
            return new Keyword(arg, value, start, end);
        }

        if (type.equals("Lambda")) {
            Map<String, Object> argsMap = (Map<String, Object>) map.get("args");
            List<Node> args = convertList(argsMap.get("args"));
            List<Node> defaults = convertList(argsMap.get("defaults"));
            Node body = deJson(map.get("body"));
            Name vararg = argsMap.get("vararg") == null ? null : new Name((String) argsMap.get("vararg"));
            Name kwarg = argsMap.get("kwarg") == null ? null : new Name((String) argsMap.get("kwarg"));
            return new Lambda(args, body, defaults, vararg, kwarg, start, end);
        }

        if (type.equals("List")) {
            List<Node> elts = convertList(map.get("elts"));
            return new NList(elts, start, end);
        }

        if (type.equals("Starred")) { // f(*[1, 2, 3, 4])
            Node value = deJson(map.get("value"));
            return new Starred(value, start, end);
        }

        if (type.equals("ListComp")) {
            Node elt = deJson(map.get("elt"));
            List<Comprehension> generators = convertListComprehension(map.get("generators"));
            return new ListComp(elt, generators, start, end);
        }

        // another name for Name in Python3 func parameters?
        if (type.equals("arg")) {
            String id = (String) map.get("arg");
            return new Name(id, start, end);
        }

        if (type.equals("SetComp")) {
            Node elt = deJson(map.get("elt"));
            List<Comprehension> generators = convertListComprehension(map.get("generators"));
            return new SetComp(elt, generators, start, end);
        }

        if (type.equals("Pass")) {
            return new Pass(start, end);
        }

        if (type.equals("Print")) {
            List<Node> values = convertList(map.get("values"));
            Node destination = deJson(map.get("destination"));
            return new Print(destination, values, start, end);
        }

        if (type.equals("Raise")) {
            Node exceptionType = deJson(map.get("type"));
            Node inst = deJson(map.get("inst"));
            Node tback = deJson(map.get("tback"));
            return new Raise(exceptionType, inst, tback, start, end);
        }

        if (type.equals("Repr")) {
            Node value = deJson(map.get("value"));
            return new Repr(value, start, end);
        }

        if (type.equals("Return")) {
            Node value = deJson(map.get("value"));
            return new Return(value, start, end);
        }

        if (type.equals("Set")) {
            List<Node> elts = convertList(map.get("elts"));
            return new Set(elts, start, end);
        }

        if (type.equals("SetComp")) {
            Node elt = deJson(map.get("elt"));
            List<Comprehension> generators = convertListComprehension(map.get("generators"));
            return new SetComp(elt, generators, start, end);
        }

        if (type.equals("Slice")) {
            Node lower = deJson(map.get("lower"));
            Node step = deJson(map.get("step"));
            Node upper = deJson(map.get("upper"));
            return new Slice(lower, step, upper, start, end);
        }

        if (type.equals("ExtSlice")) {
            List<Node> dims = convertList(map.get("dims"));
            return new ExtSlice(dims, start, end);
        }

        if (type.equals("Str")) {
            String s = (String) map.get("s");
            return new Str(s, start, end);
        }

        if (type.equals("Subscript")) {
            Node value = deJson(map.get("value"));
            Node slice = deJson(map.get("slice"));
            return new Subscript(value, slice, start, end);
        }

        if (type.equals("Try")) {
            Block body = convertBlock(map.get("body"));
            Block orelse = convertBlock(map.get("orelse"));
            List<ExceptHandler> handlers = convertListExceptHandler(map.get("handlers"));
            Block finalbody = convertBlock(map.get("finalbody"));
            return new Try(handlers, body, orelse, finalbody, start, end);
        }

        if (type.equals("TryExcept")) {
            Block body = convertBlock(map.get("body"));
            Block orelse = convertBlock(map.get("orelse"));
            List<ExceptHandler> handlers = convertListExceptHandler(map.get("handlers"));
            return new TryExcept(handlers, body, orelse, start, end);
        }

        if (type.equals("TryFinally")) {
            Block body = convertBlock(map.get("body"));
            Block finalbody = convertBlock(map.get("finalbody"));
            return new TryFinally(body, finalbody, start, end);
        }

        if (type.equals("Tuple")) {
            List<Node> elts = convertList(map.get("elts"));
            return new Tuple(elts, start, end);
        }

        if (type.equals("UnaryOp")) {
            Op op = convertOp(map.get("op"));
            Node operand = deJson(map.get("operand"));
            return new UnaryOp(op, operand, start, end);
        }

        if (type.equals("While")) {
            Node test = deJson(map.get("test"));
            Block body = convertBlock(map.get("body"));
            Block orelse = convertBlock(map.get("orelse"));
            return new While(test, body, orelse, start, end);
        }

        if (type.equals("With")) {
            List<Withitem> items = new ArrayList<>();

            Node context_expr = deJson(map.get("context_expr"));
            Node optional_vars = deJson(map.get("optional_vars"));
            Block body = convertBlock(map.get("body"));

            // Python 3 puts context_expr and optional_vars inside "items"
            if (context_expr != null) {
                Withitem item = new Withitem(context_expr, optional_vars, -1, -1);
                items.add(item);
            } else {
                List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) map.get("items");

                for (Map<String, Object> m : itemsMap) {
                    context_expr = deJson(m.get("context_expr"));
                    optional_vars = deJson(m.get("optional_vars"));
                    Withitem item = new Withitem(context_expr, optional_vars, -1, -1);
                    items.add(item);
                }
            }

            return new With(items, body, start, end);
        }

        if (type.equals("Yield")) {
            Node value = deJson(map.get("value"));
            return new Yield(value, start, end);
        }

        if (type.equals("YieldFrom")) {
            Node value = deJson(map.get("value"));
            return new Yield(value, start, end);
        }


        // ---------------------------------------------------------------------

        if (type.equals("program")) {
            Block b = (Block) deJson(map.get("body"));
            Module m = new Module(b, start, end);
            try {
                m.setFile(_.unifyPath((String) map.get("filename")));
            } catch (Exception e) {

            }
            return m;
        }

        if (type.equals("module")) {
            Block b = convertBlock(map.get("body"));
            Module m = new Module(b, start, end);
            try {
                m.setFile(_.unifyPath((String) map.get("filename")));
            } catch (Exception e) {

            }
            return m;
        }

        if (type.equals("block")) {
            List<Node> stmts = convertList(map.get("stmts"));
            return new Block(stmts, start, end);
        }

        if (type.equals("assign")) {
            Node target = deJson(map.get("target"));
            Node value = deJson(map.get("value"));
            return new Assign(target, value, start, end);
        }

        if (type.equals("ident")) {
            String id = (String) map.get("name");
            return new Name(id, start, end);
        }

        if (type.equals("int")) {
            Object n = map.get("value");
            return new Num(n, start, end);
        }

        _.die("[Please report bug]: unexpected ast node: " + map.get("type"));

        return null;
    }


    public Op convertOp(Object map) {
        String type = (String) ((Map<String, Object>) map).get("type");

        if (type.equals("Add") || type.equals("UAdd")) {
            return Op.Add;
        }

        if (type.equals("Sub") || type.equals("USub")) {
            return Op.Sub;
        }

        if (type.equals("Mult")) {
            return Op.Mul;
        }

        if (type.equals("Div")) {
            return Op.Div;
        }

        if (type.equals("Pow")) {
            return Op.Pow;
        }

        if (type.equals("Eq")) {
            return Op.Equal;
        }

        if (type.equals("Is")) {
            return Op.Eq;
        }

        if (type.equals("Lt")) {
            return Op.Lt;
        }

        if (type.equals("Gt")) {
            return Op.Gt;
        }


        if (type.equals("BitAnd")) {
            return Op.BitAnd;
        }

        if (type.equals("BitOr")) {
            return Op.BitOr;
        }

        if (type.equals("BitXor")) {
            return Op.BitXor;
        }


        if (type.equals("In")) {
            return Op.In;
        }


        if (type.equals("LShift")) {
            return Op.LShift;
        }

        if (type.equals("FloorDiv")) {
            return Op.FloorDiv;
        }

        if (type.equals("Mod")) {
            return Op.Mod;
        }

        if (type.equals("RShift")) {
            return Op.RShift;
        }

        if (type.equals("Invert")) {
            return Op.Invert;
        }

        if (type.equals("And")) {
            return Op.And;
        }

        if (type.equals("Or")) {
            return Op.Or;
        }

        if (type.equals("Not")) {
            return Op.Not;
        }

        if (type.equals("NotEq")) {
            return Op.NotEqual;
        }

        if (type.equals("IsNot")) {
            return Op.NotEq;
        }

        if (type.equals("LtE")) {
            return Op.LtE;
        }

        if (type.equals("GtE")) {
            return Op.GtE;
        }

        if (type.equals("NotIn")) {
            return Op.NotIn;
        }

        _.die("illegal operator: " + type);
        return null;
    }


    public String prettyJson(String json) {
        Map<String, Object> obj = gson.fromJson(json, Map.class);
        return gson.toJson(obj);
    }


    @Nullable
    public Process startInterpreter(String interpExe) {
        String jsonizeStr;
        Process p;

        try {
            InputStream jsonize = Thread.currentThread().getContextClassLoader().getResourceAsStream(dumpPythonResource);
            jsonizeStr = _.readWholeStream(jsonize);
        } catch (Exception e) {
            _.die("Failed to open resource file:" + dumpPythonResource);
            return null;
        }

        try {
            FileWriter fw = new FileWriter(rbStub);
            fw.write(jsonizeStr);
            fw.close();
        } catch (Exception e) {
            _.die("Failed to write into: " + rbStub);
            return null;
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(interpExe);
            builder.redirectErrorStream(true);
            builder.environment().remove("PYTHONPATH");
            p = builder.start();
        } catch (Exception e) {
            _.die("Failed to start irb");
            return null;
        }

        if (!sendCommand("load '" + rbStub + "'", p)) {
            _.die("Failed to load rbStub, please report bug");
            p.destroy();
            return null;
        }

        return p;
    }


    @Nullable
    public Node parseFile(String filename) {
        Node node = parseFileInner(filename, rubyProcess);
        if (node != null) {
            return node;
        } else {
//            Analyzer.self.failedToParse.add(filename);
            return null;
        }
    }


    private boolean sendCommand(String cmd, @NotNull Process rubyProcess) {
        _.msg("sending cmd: " + cmd);
        try {
            OutputStreamWriter writer = new OutputStreamWriter(rubyProcess.getOutputStream());
            writer.write(cmd);
            writer.write("\n");
            writer.flush();
            return true;
        } catch (Exception e) {
            _.msg("\nFailed to send command to Ruby interpreter: " + cmd);
            return false;
        }
    }


    @Nullable
    public Node parseFileInner(String filename, @NotNull Process rubyProcess) {
//        Util.msg("parsing: " + filename + " using " + pythonProcess);

        File exchange = new File(exchangeFile);
        File marker = new File(endMark);
        exchange.delete();
        marker.delete();

        String s1 = _.escapeWindowsPath(filename);
        String s2 = _.escapeWindowsPath(exchangeFile);
        String s3 = _.escapeWindowsPath(endMark);
        String dumpCommand = "parse_dump('" + s1 + "', '" + s2 + "', '" + s3 + "')";

        if (!sendCommand(dumpCommand, rubyProcess)) {
            exchange.delete();
            marker.delete();
            return null;
        }

        long waitStart = System.currentTimeMillis();
        while (!marker.exists()) {
            if (System.currentTimeMillis() - waitStart > TIMEOUT) {
                _.msg("\nTimed out while parsing: " + filename);
                exchange.delete();
                marker.delete();
                startRubyProcesses();
                return null;
            }

            try {
                Thread.sleep(1);
            } catch (Exception e) {
                exchange.delete();
                marker.delete();
                return null;
            }
        }

        String json;
        try {
            json = _.readFile(exchangeFile);
        } catch (Exception e) {
            exchange.delete();
            marker.delete();
            return null;
        }

        exchange.delete();
        marker.delete();

        Map<String, Object> map = deserialize(json);
        return deJson(map);
    }


    public static void main(String[] args) {
        RubyParser parser = new RubyParser();
        parser.parseFile(args[0]);
    }

}