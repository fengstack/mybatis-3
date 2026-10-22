/*
 *    Copyright 2009-2026 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.submitted.batch_cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.util.List;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that ExecutorType.BATCH keeps batching when the second level cache is enabled (the default), i.e. the
 * CachingExecutor delegating to the BatchExecutor does not bypass batch mode. See #3408.
 */
class BatchCacheEnabledTest {

  private static SqlSessionFactory cachedFactory;
  private static SqlSessionFactory noCacheFactory;

  @BeforeAll
  static void setUp() throws Exception {
    try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/submitted/batch_cache/mybatis-config.xml")) {
      cachedFactory = new SqlSessionFactoryBuilder().build(reader);
    }
    try (Reader reader = Resources
        .getResourceAsReader("org/apache/ibatis/submitted/batch_cache/mybatis-config-nocache.xml")) {
      noCacheFactory = new SqlSessionFactoryBuilder().build(reader);
    }
    BaseDataTest.runScript(cachedFactory.getConfiguration().getEnvironment().getDataSource(),
        "org/apache/ibatis/submitted/batch_cache/CreateDB.sql");
    BaseDataTest.runScript(noCacheFactory.getConfiguration().getEnvironment().getDataSource(),
        "org/apache/ibatis/submitted/batch_cache/CreateDB.sql");
    assertTrue(cachedFactory.getConfiguration().isCacheEnabled());
    assertTrue(!noCacheFactory.getConfiguration().isCacheEnabled());
  }

  @Test
  void shouldBatchUpdatesWithSecondLevelCacheEnabled() {
    try (SqlSession sqlSession = cachedFactory.openSession(ExecutorType.BATCH)) {
      Mapper mapper = sqlSession.getMapper(Mapper.class);
      // a query first, so that the second level cache is actually engaged
      assertNotNull(mapper.getUser(1));
      mapper.insertUser(2, "User2");
      mapper.insertUser(3, "User3");
      mapper.insertUser(4, "User4");
      List<BatchResult> batchResults = sqlSession.flushStatements();
      assertEquals(1, batchResults.size());
      assertArrayEquals(new int[] { 1, 1, 1 }, batchResults.get(0).getUpdateCounts());
      sqlSession.commit();
    }
    try (SqlSession sqlSession = cachedFactory.openSession()) {
      assertEquals(4, sqlSession.getMapper(Mapper.class).countUsers());
    }
  }

  @Test
  void shouldBatchUpdatesWithoutSecondLevelCache() {
    try (SqlSession sqlSession = noCacheFactory.openSession(ExecutorType.BATCH)) {
      Mapper mapper = sqlSession.getMapper(Mapper.class);
      assertNotNull(mapper.getUser(1));
      mapper.insertUser(2, "User2");
      mapper.insertUser(3, "User3");
      mapper.insertUser(4, "User4");
      List<BatchResult> batchResults = sqlSession.flushStatements();
      assertEquals(1, batchResults.size());
      assertArrayEquals(new int[] { 1, 1, 1 }, batchResults.get(0).getUpdateCounts());
      sqlSession.commit();
    }
    try (SqlSession sqlSession = noCacheFactory.openSession()) {
      assertEquals(4, sqlSession.getMapper(Mapper.class).countUsers());
    }
  }
}
