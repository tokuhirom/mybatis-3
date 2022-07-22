/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.binding.issue2620;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.domain.blog.Author;
import org.apache.ibatis.domain.blog.Blog;
import org.apache.ibatis.domain.blog.Post;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Issue2620Test {
  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setup() throws Exception {
  }

  @Test
  void shouldFailForBothOneAndMany() throws Exception {
    ExecutorService executorService = Executors.newFixedThreadPool(2);

    Set<Exception> failMessages = new HashSet<>();

    for (int i = 0; i < 1000; i++) {
      System.out.println("--------------- " + i);
      DataSource dataSource = BaseDataTest.createBlogDataSource();
      BaseDataTest.runScript(dataSource, BaseDataTest.BLOG_DDL);
      BaseDataTest.runScript(dataSource, BaseDataTest.BLOG_DATA);
      TransactionFactory transactionFactory = new JdbcTransactionFactory();
      Environment environment = new Environment("Production", transactionFactory, dataSource);
      Configuration configuration = new Configuration(environment);
      configuration.setLazyLoadingEnabled(true);
      configuration.getTypeAliasRegistry().registerAlias(Blog.class);
      configuration.getTypeAliasRegistry().registerAlias(Post.class);
      configuration.getTypeAliasRegistry().registerAlias(Author.class);
      configuration.addMapper(TagMapper.class);
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

      Future<Exception> future = executorService.submit(() -> {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
          TagMapper mapper = sqlSession.getMapper(TagMapper.class);
          mapper.selectAll();
          return null;
        } catch (Exception e) {
          return e;
        }
      });

      configuration.addMapper(Issue2620Mapper.class);

      Exception exception = future.get();
      if (exception != null) {
        exception.printStackTrace();
        failMessages.add(exception);
      }
    }

    executorService.shutdown();
    assertTrue(failMessages.isEmpty());
  }
}
