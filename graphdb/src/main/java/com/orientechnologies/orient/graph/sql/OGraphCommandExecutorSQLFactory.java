/*
 * Copyright 2012 Orient Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.graph.sql;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.db.record.ODatabaseRecordTx;
import com.orientechnologies.orient.core.exception.OCommandExecutionException;
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLAbstract;
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLFactory;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;

/**
 * Graph related command operator executor factory. It's auto-discovered.
 * 
 * @author Luca Garulli
 */
public class OGraphCommandExecutorSQLFactory implements OCommandExecutorSQLFactory {

  private static final Map<String, Class<? extends OCommandExecutorSQLAbstract>> COMMANDS;

  static {

    // COMMANDS
    final Map<String, Class<? extends OCommandExecutorSQLAbstract>> commands = new HashMap<String, Class<? extends OCommandExecutorSQLAbstract>>();

    commands.put(OCommandExecutorSQLCreateEdge.NAME, OCommandExecutorSQLCreateEdge.class);
    commands.put(OCommandExecutorSQLDeleteEdge.NAME, OCommandExecutorSQLDeleteEdge.class);
    commands.put(OCommandExecutorSQLCreateVertex.NAME, OCommandExecutorSQLCreateVertex.class);
    commands.put(OCommandExecutorSQLDeleteVertex.NAME, OCommandExecutorSQLDeleteVertex.class);

    COMMANDS = Collections.unmodifiableMap(commands);
  }

  public interface GraphCallBack<T> {
    T call(OrientBaseGraph graph);
  }

  /**
   * Returns a Transactional OrientGraph implementation from the current database in thread local.
   *
   * @param autoStartTx
   *          Whether returned graph will start transaction before each operation till commit automatically or user should do it
   *          explicitly be calling {@link OrientGraph#getRawGraph()} method {@link ODatabaseDocumentTx#begin()}.
   *
   * @return Transactional OrientGraph implementation from the current database in thread local.
   */
  public static OrientGraph getGraph(final boolean autoStartTx) {
    ODatabaseRecord database = ODatabaseRecordThreadLocal.INSTANCE.get();
    if (!(database instanceof ODatabaseDocumentTx))
      database = new ODatabaseDocumentTx((ODatabaseRecordTx) database);

    return new OrientGraph((ODatabaseDocumentTx) database, autoStartTx);
  }

  /**
   * @return a Non Transactional OrientGraph implementation from the current database in thread local.
   */
  public static OrientBaseGraph getNoTxGraph() {
    ODatabaseRecord database = ODatabaseRecordThreadLocal.INSTANCE.get();
    if (!(database instanceof ODatabaseDocumentTx))
      database = new ODatabaseDocumentTx((ODatabaseRecordTx) database);

    return new OrientGraphNoTx((ODatabaseDocumentTx) database);
  }

  public static <T> T runInTx(final OrientGraph graph, final GraphCallBack<T> callBack) {
    final ODatabaseRecord databaseRecord = getDatabase();
    final boolean txWasActive = databaseRecord.getTransaction().isActive();

    if (!txWasActive)
      graph.getRawGraph().begin();

    try {
      final T result = callBack.call(graph);

      if (!txWasActive)
        graph.commit();

      return result;
    } catch (RuntimeException e) {
      if (!txWasActive)
        graph.rollback();

      throw e;
    }
  }

  public static <T> T runInTx(final GraphCallBack<T> callBack) {
    return runInTx(OGraphCommandExecutorSQLFactory.getGraph(false), callBack);
  }

  public static ODatabaseRecord getDatabase() {
    return ODatabaseRecordThreadLocal.INSTANCE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Set<String> getCommandNames() {
    return COMMANDS.keySet();
  }

  /**
   * {@inheritDoc}
   */
  public OCommandExecutorSQLAbstract createCommand(final String name) throws OCommandExecutionException {
    final Class<? extends OCommandExecutorSQLAbstract> clazz = COMMANDS.get(name);

    if (clazz == null) {
      throw new OCommandExecutionException("Unknown command name :" + name);
    }

    try {
      return clazz.newInstance();
    } catch (Exception e) {
      throw new OCommandExecutionException("Error in creation of command " + name
          + "(). Probably there is not an empty constructor or the constructor generates errors", e);
    }
  }
}
