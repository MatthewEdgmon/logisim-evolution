/**
 * This file is part of Logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Logisim-evolution.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + Haute École Spécialisée Bernoise
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 *   + REDS Institute - HEIG-VD, Yverdon-les-Bains, Switzerland
 *     http://reds.heig-vd.ch
 * This version of the project is currently maintained by:
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.analyze.model;
import static com.cburch.logisim.analyze.model.Strings.S;

import java.util.HashSet;

public abstract class Expression {

  public static interface Visitor<T> {
    public default T visitVariable(String name) { return null; }
    public default T visitConstant(int value) { return null; }
    public default T visitNot(Expression a) { return null; }
    public default T visitBinary(Expression a, Expression b, Op op) {
      a.visit(this);
      b.visit(this);
      return null;
    }

    public default T visitAnd(Expression a, Expression b) { return visitBinary(a, b, Op.AND); }
    public default T visitOr(Expression a, Expression b) { return visitBinary(a, b, Op.OR); }
    public default T visitXor(Expression a, Expression b) { return visitBinary(a, b, Op.XOR); }
    public default T visitEq(Expression a, Expression b) { return visitBinary(a, b, Op.EQ); }
  }

  // internal, just for fast eval
  protected static interface IntVisitor {
    public default int visitVariable(String name) { return 0; }
    public default int visitConstant(int value) { return 0; }
    public default int visitNot(Expression a) { return 0; }

    public default int visitAnd(Expression a, Expression b) { return 0; }
    public default int visitOr(Expression a, Expression b) { return 0; }
    public default int visitXor(Expression a, Expression b) { return 0; }
    public default int visitEq(Expression a, Expression b) { return 0; }
  }

  public boolean contains(Op o) {
    return o == visit(new Visitor<Op>() {
      public Op visitBinary(Expression a, Expression b, Op op) {
        return (op == o || a.visit(this) == o || b.visit(this) == o) ? o : null;
      }
      public Op visitNot(Expression a) { return a.visit(this); }
    });
  }

  public boolean evaluate(final Assignments assignments) {
    int ret = visit(new IntVisitor() {
      public int visitAnd(Expression a, Expression b) {
        return a.visit(this) & b.visit(this);
      }

      public int visitConstant(int value) {
        return value;
      }

      public int visitNot(Expression a) {
        return ~a.visit(this);
      }

      public int visitOr(Expression a, Expression b) {
        return a.visit(this) | b.visit(this);
      }

      public int visitVariable(String name) {
        return assignments.get(name) ? 1 : 0;
      }

      public int visitXor(Expression a, Expression b) {
        return a.visit(this) ^ b.visit(this);
      }

      public int visitEq(Expression a, Expression b) {
        return ~(a.visit(this) ^ b.visit(this)&1);
      }
    });
    return (ret & 1) != 0;
  }

  public abstract int getPrecedence();
  public abstract Op getOp();

  public boolean isCircular() {
    final HashSet<Expression> visited = new HashSet<Expression>();
    visited.add(this);
    Object loop = new Object();
    return loop == visit(new Visitor<Object>() {
      public Object visitBinary(Expression a, Expression b) {
        if (!visited.add(a))
          return loop;
        if (a.visit(this) == loop)
          return loop;
        visited.remove(a);

        if (!visited.add(b))
          return loop;
        if (b.visit(this) == loop)
          return loop;
        visited.remove(b);

        return null;
      }

      public Object visitNot(Expression a) {
        if (!visited.add(a))
          return loop;
        if (a.visit(this) == loop)
          return loop;
        visited.remove(a);
        return null;
      }
    });
  }

  public boolean isCnf() {
    Object cnf = new Object();
    return cnf == visit(new Visitor<Object>() {
      int level = 0;

      public Object visitAnd(Expression a, Expression b) {
        if (level > 1)
          return null;
        int oldLevel = level;
        level = 1;
        Object ret = a.visit(this) == cnf && b.visit(this) == cnf ? cnf : null;
        level = oldLevel;
        return ret;
      }

      public Object visitConstant(int value) {
        return cnf;
      }

      public Object visitNot(Expression a) {
        if (level == 2)
          return null;
        int oldLevel = level;
        level = 2;
        Object ret = a.visit(this);
        level = oldLevel;
        return ret;
      }

      public Object visitOr(Expression a, Expression b) {
        if (level > 0)
          return null;
        return a.visit(this) == cnf && b.visit(this) == cnf ? cnf : null;
      }

      public Object visitVariable(String name) {
        return cnf;
      }

      public Object visitXor(Expression a, Expression b) {
        return null;
      }

      public Object visitEq(Expression a, Expression b) {
        return null;
      }
    });
  }

  Expression removeVariable(final String input) {
    return visit(new Visitor<Expression>() {
      public Expression visitAnd(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        if (l == null)
          return r;
        if (r == null)
          return l;
        return Expressions.and(l, r);
      }

      public Expression visitConstant(int value) {
        return Expressions.constant(value);
      }

      public Expression visitNot(Expression a) {
        Expression l = a.visit(this);
        if (l == null)
          return null;
        return Expressions.not(l);
      }

      public Expression visitOr(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        if (l == null)
          return r;
        if (r == null)
          return l;
        return Expressions.or(l, r);
      }

      public Expression visitVariable(String name) {
        return name.equals(input) ? null : Expressions.variable(name);
      }

      public Expression visitXor(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        if (l == null)
          return r;
        if (r == null)
          return l;
        return Expressions.xor(l, r);
      }

      public Expression visitEq(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        if (l == null)
          return r;
        if (r == null)
          return l;
        return Expressions.eq(l, r);
      }
    });
  }

  Expression replaceVariable(final String oldName, final String newName) {
    return visit(new Visitor<Expression>() {
      public Expression visitAnd(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        return Expressions.and(l, r);
      }

      public Expression visitConstant(int value) {
        return Expressions.constant(value);
      }

      public Expression visitNot(Expression a) {
        Expression l = a.visit(this);
        return Expressions.not(l);
      }

      public Expression visitOr(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        return Expressions.or(l, r);
      }

      public Expression visitVariable(String name) {
        return Expressions.variable(name.equals(oldName) ? newName
            : name);
      }

      public Expression visitXor(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        return Expressions.xor(l, r);
      }

      public Expression visitEq(Expression a, Expression b) {
        Expression l = a.visit(this);
        Expression r = b.visit(this);
        return Expressions.eq(l, r);
      }
    });
  }

  public static enum Notation {
    ENGINEERING(0), MATHEMATICS(1), PROGRAMMING(2);

    public final int Id;

    private Notation(int id) { Id = id; }

    public String toString() {
      String key = name().toLowerCase() + "Notation";
      return S.get(key);
    }
  }

  public static enum Op {
    EQ(0,2), OR(1,2), XOR(2,2), AND(3,2), NOT(4,1);

    public final int Id, Level, Arity;
    public final String[] Sym;

    private Op(int id, int arity) {
      Id = id;
      Level = id; // so far, precedence level coincides with id
      Arity = arity;
      Sym = new String[] { OPSYM[0][Id], OPSYM[1][Id], OPSYM[2][Id] };
    }
  }

  // Notation choices:
  public static final String[][] OPSYM = {
    { " = ", " \u2228 ", " \u2295 ", " \u2227 ", "~", }, // engineering
    { " = ", " + ", "\u2295", " \u22C5 ", "\u00AC", }, // mathematics
    { " == ", " || ", " ^ ", " && ", "!", }, // programming
  };

  @Override
  public String toString() {
    return toString(Notation.ENGINEERING);
  }

  public String toString(Notation notation) {
    final StringBuilder text = new StringBuilder();
    visit(new Visitor<Void>() {
      public Void visitBinary(Expression a, Expression b, Op op) {
        if (a.getPrecedence() < op.Level) {
          text.append("(");
          a.visit(this);
          text.append(")");
        } else {
          a.visit(this);
        }
        text.append(op.Sym[notation.Id]);
        if (b.getPrecedence() < op.Level) {
          text.append("(");
          b.visit(this);
          text.append(")");
        } else {
          b.visit(this);
        }
        return null;
      }

      public Void visitConstant(int value) {
        text.append(Integer.toString(value, 16));
        return null;
      }

      public Void visitNot(Expression a) {
        text.append(Op.NOT.Sym[notation.Id]);
        if (a.getPrecedence() < Op.NOT.Level) {
          text.append("(");
          a.visit(this);
          text.append(")");
        } else {
          a.visit(this);
        }
        return null;
      }

      public Void visitVariable(String name) {
        text.append(name);
        return null;
      }

    });
    return text.toString();
  }

  public abstract <T> T visit(Visitor<T> visitor);

  abstract int visit(IntVisitor visitor);

}
