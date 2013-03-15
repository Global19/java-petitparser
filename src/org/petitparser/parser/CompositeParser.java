package org.petitparser.parser;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import org.petitparser.Parsers;
import org.petitparser.utils.Transformations;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

/**
 * Helper to compose complex grammars from various primitive parsers. To create
 * a new composite grammar subclass {@link CompositeParser}. Override the method
 * {@link #initialize} and for every production call
 * {@link CompositeParser#def(String, Parser)} giving the parsers a name. The
 * start production must be named 'start'. To refer to other productions use
 * {@link CompositeParser#ref(String)}. To redefine or attach actions to
 * productions use {@link CompositeParser#redef(String, Function)},
 * {@link CompositeParser#redef(String, Parser) and
 * {@link CompositeParser#action(String, Function)}.
 *
 * @author Lukas Renggli (renggli@gmail.com)
 */
public abstract class CompositeParser extends DelegateParser {

  private boolean initialized = false;

  private final Map<String, Parser> defined = Maps.newHashMap();
  private final Map<String, DelegateParser> undefined = Maps.newHashMap();

  /**
   * Lazily initializes the delegate by calling {@link CompositeParser#initialize()}.
   */
  @Override
  public Parser getDelegate() {
    if (!initialized) {
      initialize();
      replace(delegate, ref("start"));
      for (Map.Entry<String, DelegateParser> entry : undefined.entrySet()) {
        checkState(defined.containsKey(entry.getKey()), "Undefined production: ", entry.getKey());
        entry.getValue().replace(entry.getValue().getDelegate(), defined.get(entry.getKey()));
      }
      replace(delegate, Transformations.removeDelegates(ref("start")));
      initialized = true;
    }
    return delegate;
  }

  /**
   * Automatically called by the framework to initialize the grammar.
   */
  protected abstract void initialize();

  /**
   * Returns a reference to the production with the given {@code name}.
   *
   * This method works during initialization, where it returns delegate
   * parsers that is eventually replaced the real parsers. This method
   * also works after initialization, where it returns the defined parser
   * (mostly useful for testing).
   */
  public final Parser ref(String name) {
    if (initialized) {
      return defined.get(name);
    } else if (undefined.containsKey(name)) {
      return undefined.get(name);
    } else {
      DelegateParser parser = new DelegateParser(
          Parsers.failure("Uninitalized production: " + name));
      undefined.put(name, parser);
      return parser;
    }
  }

  /**
   * Defines a production with a {@code name} and a {@code parser}. Only call
   * this method during initialization.
   */
  protected final void def(String name, Parser parser) {
    checkState(!initialized, "Parser is already initialized");
    checkState(!defined.containsKey(name), "Duplicate production: ", name);
    defined.put(
        checkNotNull(name, "Invalid name: ", name),
        checkNotNull(parser, "Invalid parser: ", parser));
  }

  /**
   * Redefines an existing production with a {@code name} and a new
   * {@code parser}. Only call this method during initialization.
   */
  protected final void redef(String name, Parser parser) {
    checkState(!initialized, "Parser is already initialized");
    checkState(defined.containsKey(name), "Undefined production: ", name);
    defined.put(
        checkNotNull(name, "Invalid name: ", name),
        checkNotNull(parser, "Invalid parser: ", parser));
  }

  /**
   * Redefines an existing production with a {@code name} and a {@code function}
   * producing a new parser. Only call this method during initialization.
   */
  protected final void redef(String name, Function<Parser, Parser> function) {
    redef(name, function.apply(defined.get(name)));
  }

  /**
   * Attaches an action {@code function} to an existing production {@code name}.
   * Only call this method during initialization.
   */
  protected final <S, T> void action(String name, final Function<S, T> function) {
    redef(name, new Function<Parser, Parser>() {
      @Override
      public Parser apply(Parser parser) {
        return parser.map(function);
      }
    });
  }

}