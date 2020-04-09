/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.authorization;


import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Permissions.CREATE;
import static org.camunda.bpm.engine.authorization.Permissions.CREATE_INSTANCE;
import static org.camunda.bpm.engine.authorization.Permissions.DELETE;
import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Permissions.READ_TASK;
import static org.camunda.bpm.engine.authorization.Permissions.TASK_ASSIGN;
import static org.camunda.bpm.engine.authorization.Permissions.TASK_WORK;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE_TASK;
import static org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions.UPDATE_TASK_VARIABLE;
import static org.camunda.bpm.engine.authorization.TaskPermissions.UPDATE_VARIABLE;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_INSTANCE;
import static org.camunda.bpm.engine.authorization.Resources.TASK;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.authorization.*;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.impl.AbstractQuery;
import org.camunda.bpm.engine.impl.TaskServiceImpl;
import org.camunda.bpm.engine.impl.cfg.auth.DefaultAuthorizationProvider;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.runtime.VariableInstanceQuery;
import org.camunda.bpm.engine.task.IdentityLink;
import org.camunda.bpm.engine.task.IdentityLinkType;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.value.TypedValue;

/**
 * @author Roman Smirnov
 *
 */
public class TaskAuthorizationTest extends AuthorizationTest {

  protected static final String PROCESS_KEY = "oneTaskProcess";
  protected static final String CASE_KEY = "oneTaskCase";
  protected static final String DEMO_ASSIGNEE_PROCESS_KEY = "demoAssigneeProcess";
  protected static final String CANDIDATE_USERS_PROCESS_KEY = "candidateUsersProcess";
  protected static final String CANDIDATE_GROUPS_PROCESS_KEY = "candidateGroupsProcess";
  protected static final String INVALID_PERMISSION = "invalidPermission";
  protected String deploymentId;

  @Override
  public void setUp() throws Exception {
    deploymentId = createDeployment(null,
        "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml",
        "org/camunda/bpm/engine/test/api/authorization/oneTaskCase.cmmn",
        "org/camunda/bpm/engine/test/api/authorization/oneTaskProcess.bpmn20.xml",
        "org/camunda/bpm/engine/test/api/authorization/candidateUsersProcess.bpmn20.xml",
        "org/camunda/bpm/engine/test/api/authorization/candidateGroupsProcess.bpmn20.xml").getId();
    super.setUp();
  }

  @Override
  public void tearDown() {
    super.tearDown();
    deleteDeployment(deploymentId);

    processEngineConfiguration.getCommandExecutorTxRequired().execute(new Command<Void>() {
      public Void execute(CommandContext commandContext) {

        List<HistoricVariableInstance> variables = historyService.createHistoricVariableInstanceQuery().includeDeleted().list();
        for (HistoricVariableInstance variable : variables) {
          commandContext.getDbEntityManager().delete((HistoricVariableInstanceEntity) variable);
        }
        return null;
      }
    });
  }

  // task query ///////////////////////////////////////////////////////

  public void testSimpleQueryWithTaskInsideProcessWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 0);
  }

  public void testSimpleQueryWithTaskInsideProcessWithReadPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, READ);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);
  }

  public void testSimpleQueryWithTaskInsideProcessWithReadPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    createGrantAuthorization(TASK, ANY, userId, READ);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);
  }

  public void testSimpleQueryWithTaskInsideProcessWithReadPermissionOnOneTaskProcess() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);
  }

  public void testSimpleQueryWithTaskInsideProcessWithReadPermissionOnAnyProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    createGrantAuthorization(PROCESS_DEFINITION, ANY, userId, READ_TASK);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);
  }

  public void testSimpleQueryWithMultiple() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(PROCESS_DEFINITION, ANY, userId, READ_TASK);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK);
    createGrantAuthorization(TASK, ANY, userId, READ);
    createGrantAuthorization(TASK, taskId, userId, READ);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);
  }

  public void testQueryWithTaskInsideProcessWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);

    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 0);
  }

  public void testQueryWithTaskInsideProcessWithReadPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);

    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);

    disableAuthorization();
    String taskId = taskService.createTaskQuery().processDefinitionKey(PROCESS_KEY).listPage(0, 1).get(0).getId();
    enableAuthorization();

    createGrantAuthorization(TASK, taskId, userId, READ);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);
  }

  public void testQueryWithTaskInsideProcessWithReadPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);

    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);

    createGrantAuthorization(TASK, ANY, userId, READ);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 7);
  }

  public void testQueryWithTaskInsideProcessWithReadPermissionOnOneTaskProcess() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);

    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 3);
  }

  public void testQueryWithTaskInsideProcessWithReadPermissionOnAnyProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);
    startProcessInstanceByKey(PROCESS_KEY);

    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);
    startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);

    createGrantAuthorization(PROCESS_DEFINITION, ANY, userId, READ_TASK);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 7);
  }

  public void testQueryWithTaskInsideCaseWithoutAuthorization() {
    // given
    createCaseInstanceByKey(CASE_KEY);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);
  }

  public void testQueryWithStandaloneTaskWithoutAuthorization() {
    // given
    String taskId = "newTask";
    createTask(taskId);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 0);

    deleteTask(taskId, true);
  }

  public void testQueryWithStandaloneTaskWithReadPermissionOnTask() {
    // given
    String taskId = "newTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, READ);

    // when
    TaskQuery query = taskService.createTaskQuery();

    // then
    verifyQueryResults(query, 1);

    deleteTask(taskId, true);
  }

  // new task /////////////////////////////////////////////////////////////

  public void testNewTaskWithoutAuthorization() {
    // given

    try {
      // when
      taskService.newTask();
      fail("Exception expected: It should not be possible to create a new task.");
    } catch (AuthorizationException e) {

      // then
      testHelper.assertTextPresent("The user with id 'test' does not have 'CREATE' permission on resource 'Task'", e.getMessage());
    }
  }

  public void testNewTask() {
    // given
    createGrantAuthorization(TASK, ANY, userId, CREATE);

    // when
    Task task = taskService.newTask();

    // then
    assertNotNull(task);
  }

  // save task (insert) //////////////////////////////////////////////////////////

  public void testSaveTaskInsertWithoutAuthorization() {
    // given
    TaskEntity task = new TaskEntity();

    try {
      // when
      taskService.saveTask(task);
      fail("Exception expected: It should not be possible to save a task.");
    } catch (AuthorizationException e) {

      // then
      testHelper.assertTextPresent("The user with id 'test' does not have 'CREATE' permission on resource 'Task'", e.getMessage());
    }
  }

  public void testSaveTaskInsert() {
    // given
    TaskEntity task = new TaskEntity();
    task.setAssignee("demo");

    createGrantAuthorization(TASK, ANY, userId, CREATE);

    // when
    taskService.saveTask(task);

    // then
    task = (TaskEntity) selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    String taskId = task.getId();
    deleteTask(taskId, true);
  }

  public void testSaveAndUpdateTaskWithTaskAssignPermission() {
    // given
    TaskEntity task = new TaskEntity();
    task.setAssignee("demo");

    createGrantAuthorization(TASK, ANY, userId, CREATE, TASK_ASSIGN);

    // when
    taskService.saveTask(task);

    task.delegate("demoNew");

    taskService.saveTask(task);

    // then
    task = (TaskEntity) selectSingleTask();
    assertNotNull(task);
    assertEquals("demoNew", task.getAssignee());

    String taskId = task.getId();
    deleteTask(taskId, true);
  }

  // save (standalone) task (update) //////////////////////////////////////////////////////////

  public void testSaveStandaloneTaskUpdateWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    Task task = selectSingleTask();

    try {
      // when
      taskService.saveTask(task);
      fail("Exception expected: It should not be possible to save a task.");
    } catch (AuthorizationException e) {

      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN'", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testSaveStandaloneTaskUpdate() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    Task task = selectSingleTask();
    task.setAssignee("demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    deleteTask(taskId, true);
  }

  // save (process) task (update) //////////////////////////////////////////////////////////

  public void testSaveProcessTaskUpdateWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    Task task = selectSingleTask();

    try {
      // when
      taskService.saveTask(task);
      fail("Exception expected: It should not be possible to save a task.");
    } catch (AuthorizationException e) {

      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(task.getId(), message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testSaveProcessTaskUpdateWithUpdatePermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    Task task = selectSingleTask();
    task.setAssignee("demo");

    createGrantAuthorization(TASK, task.getId(), userId, UPDATE);

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testSaveProcessTaskUpdateWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    Task task = selectSingleTask();
    task.setAssignee("demo");

    createGrantAuthorization(TASK, task.getId(), userId, TASK_ASSIGN);

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testSaveProcessTaskUpdateWithUpdatePermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    Task task = selectSingleTask();
    task.setAssignee("demo");

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testSaveProcessTaskUpdateWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    Task task = selectSingleTask();
    task.setAssignee("demo");

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testSaveProcessTaskUpdateWithUpdateTasksPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    Task task = selectSingleTask();
    task.setAssignee("demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testSaveProcessTaskUpdateWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    Task task = selectSingleTask();
    task.setAssignee("demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // save (case) task (update) //////////////////////////////////////////////////////////

  public void testSaveCaseTaskUpdate() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    Task task = selectSingleTask();
    task.setAssignee("demo");

    // when
    taskService.saveTask(task);

    // then
    task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // delete task ///////////////////////////////////////////////////////////////////////

  public void testDeleteTaskWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.deleteTask(taskId);
      fail("Exception expected: It should not be possible to delete a task.");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have 'DELETE' permission on resource 'myTask' of type 'Task'", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testDeleteTask() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, DELETE);

    // when
    taskService.deleteTask(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);

    // triggers a db clean up
    deleteTask(taskId, true);
  }

  // delete tasks ///////////////////////////////////////////////////////////////////////

  public void testDeleteTasksWithoutAuthorization() {
    // given
    String firstTaskId = "myTask1";
    createTask(firstTaskId);
    String secondTaskId = "myTask2";
    createTask(secondTaskId);

    try {
      // when
      taskService.deleteTasks(Arrays.asList(firstTaskId, secondTaskId));
      fail("Exception expected: It should not be possible to delete tasks.");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have 'DELETE' permission on resource 'myTask1' of type 'Task'", e.getMessage());
    }

    deleteTask(firstTaskId, true);
    deleteTask(secondTaskId, true);
  }

  public void testDeleteTasksWithDeletePermissionOnFirstTask() {
    // given
    String firstTaskId = "myTask1";
    createTask(firstTaskId);
    createGrantAuthorization(TASK, firstTaskId, userId, DELETE);

    String secondTaskId = "myTask2";
    createTask(secondTaskId);

    try {
      // when
      taskService.deleteTasks(Arrays.asList(firstTaskId, secondTaskId));
      fail("Exception expected: It should not be possible to delete tasks.");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have 'DELETE' permission on resource 'myTask2' of type 'Task'", e.getMessage());
    }

    deleteTask(firstTaskId, true);
    deleteTask(secondTaskId, true);
  }

  public void testDeleteTasks() {
    // given
    String firstTaskId = "myTask1";
    createTask(firstTaskId);
    String secondTaskId = "myTask2";
    createTask(secondTaskId);

    createGrantAuthorization(TASK, ANY, userId, DELETE);

    // when
    taskService.deleteTasks(Arrays.asList(firstTaskId, secondTaskId));

    // then
    Task task = selectSingleTask();
    assertNull(task);

    // triggers a db clean up
    deleteTask(firstTaskId, true);
    deleteTask(secondTaskId, true);
  }

  // set assignee on standalone task /////////////////////////////////////////////

  public void testStandaloneTaskSetAssigneeWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.setAssignee(taskId, "demo");
      fail("Exception expected: It should not be possible to set an assignee");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetAssignee() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetAssigneeWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    deleteTask(taskId, true);
  }

  // set assignee on process task /////////////////////////////////////////////

  public void testProcessTaskSetAssigneeWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.setAssignee(taskId, "demo");
      fail("Exception expected: It should not be possible to set an assignee");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskSetAssigneeWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskSetAssigneeWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskSetAssigneeWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskSetAssigneeWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskSetAssigneeWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskSetAssigneeWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskSetAssignee() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // set assignee on case task /////////////////////////////////////////////

  public void testCaseTaskSetAssignee() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // set owner on standalone task /////////////////////////////////////////////

  public void testStandaloneTaskSetOwnerWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.setOwner(taskId, "demo");
      fail("Exception expected: It should not be possible to set an owner");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetOwner() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetOwnerWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());

    deleteTask(taskId, true);
  }

  // set owner on process task /////////////////////////////////////////////

  public void testProcessTaskSetOwnerWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.setOwner(taskId, "demo");
      fail("Exception expected: It should not be possible to set an owner");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskSetOwnerWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  public void testProcessTaskSetOwnerWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  public void testProcessTaskSetOwnerWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  public void testProcessTaskSetOwnerWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  public void testProcessTaskSetOwnerWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  public void testProcessTaskSetOwnerWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  public void testProcessTaskSetOwner() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  public void testProcessTaskSetOwnerWithTaskAssignPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  // set owner on case task /////////////////////////////////////////////

  public void testCaseTaskSetOwner() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.setOwner(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getOwner());
  }

  // add candidate user ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskAddCandidateUserWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.addCandidateUser(taskId, "demo");
      fail("Exception expected: It should not be possible to add a candidate user");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddCandidateUser() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());

    deleteTask(taskId, true);
  }

  // add candidate user ((process) task) /////////////////////////////////////////////

  public void testProcessTaskAddCandidateUserWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.addCandidateUser(taskId, "demo");
      fail("Exception expected: It should not be possible to add a candidate user");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskAddCandidateUserWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateUserWithTaskAssignPermissionRevokeOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createRevokeAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    try {
      // when
      taskService.addCandidateUser(taskId, "demo");
      fail("Exception expected: It should not be possible to add an user identity link");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }
  }

  public void testProcessTaskAddCandidateUserWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateUserWithGrantTaskAssignAndRevokeUpdatePermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createRevokeAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateUserWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateUserWithTaskAssignPersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateUserWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateUserWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateUser() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // add candidate user ((case) task) /////////////////////////////////////////////

  public void testCaseTaskAddCandidateUser() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // add candidate group ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskAddCandidateGroupWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.addCandidateGroup(taskId, "accounting");
      fail("Exception expected: It should not be possible to add a candidate group");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddCandidateGroup() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddCandidateGroupWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());

    deleteTask(taskId, true);
  }

  // add candidate group ((process) task) /////////////////////////////////////////////

  public void testProcessTaskAddCandidateGroupWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.addCandidateGroup(taskId, "accounting");
      fail("Exception expected: It should not be possible to add a candidate group");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskAddCandidateGroupWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroupWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroupWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroupWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroupWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroupWithTaskAssignPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroup() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroupWithTaskAssignPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddCandidateGroupWithTaskAssignPermissionRevoked() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createRevokeAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);
    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // add candidate group ((case) task) /////////////////////////////////////////////

  public void testCaseTaskAddCandidateGroup() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.addCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // add user identity link ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskAddUserIdentityLinkWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to add an user identity link");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddUserIdentityLink() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddUserIdentityLinkWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);


    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());

    deleteTask(taskId, true);
  }

  // add user identity link ((process) task) /////////////////////////////////////////////

  public void testProcessTaskAddUserIdentityLinkWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to add an user identity link");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskAddUserIdentityLinkWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddUserIdentityLinkWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddUserIdentityLinkWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddUserIdentityLinkWithTaskAssignPersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddUserIdentityLinkWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddUserIdentityLinkWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddUserIdentityLink() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // add user identity link ((case) task) /////////////////////////////////////////////

  public void testCaseTaskAddUserIdentityLink() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.addUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("demo", identityLink.getUserId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // add group identity link ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskAddGroupIdentityLinkWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to add a group identity link");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddGroupIdentityLink() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());

    deleteTask(taskId, true);
  }

  // add group identity link ((process) task) /////////////////////////////////////////////

  public void testProcessTaskAddGroupIdentityLinkWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to add a group identity link");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskAddGroupIdentityLinkWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddGroupIdentityLinkWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddGroupIdentityLinkWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  public void testProcessTaskAddGroupIdentityLink() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // add group identity link ((case) task) /////////////////////////////////////////////

  public void testCaseTaskAddGroupIdentityLink() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.addGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertEquals(1, linksForTask.size());

    IdentityLink identityLink = linksForTask.get(0);
    assertNotNull(identityLink);

    assertEquals("accounting", identityLink.getGroupId());
    assertEquals(IdentityLinkType.CANDIDATE, identityLink.getType());
  }

  // delete candidate user ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskDeleteCandidateUserWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    try {
      // when
      taskService.deleteCandidateUser(taskId, "demo");
      fail("Exception expected: It should not be possible to delete a candidate user");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDeleteCandidateUser() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDeleteCandidateUserWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  // delete candidate user ((process) task) /////////////////////////////////////////////

  public void testProcessTaskDeleteCandidateUserWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    try {
      // when
      taskService.deleteCandidateUser(taskId, "demo");
      fail("Exception expected: It should not be possible to delete a candidate user");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskDeleteCandidateUserWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateUserWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateUserWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateUserWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateUserWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateUserWithTaskAssignPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateUser() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // delete candidate user ((case) task) /////////////////////////////////////////////

  public void testCaseTaskDeleteCandidateUser() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    // when
    taskService.deleteCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // delete candidate group ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskDeleteCandidateGroupWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateGroup(taskId, "accounting");

    try {
      // when
      taskService.deleteCandidateGroup(taskId, "accounting");
      fail("Exception expected: It should not be possible to delete a candidate group");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDeleteCandidateGroup() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDeleteCandidateGroupWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  // delete candidate group ((process) task) /////////////////////////////////////////////

  public void testProcessTaskDeleteCandidateGroupWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    try {
      // when
      taskService.deleteCandidateGroup(taskId, "accounting");
      fail("Exception expected: It should not be possible to delete a candidate group");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskDeleteCandidateGroupWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateGroupWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateGroupWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateGroupWithTaskAssignPersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateGroupWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateGroupWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteCandidateGroup() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // delete candidate group ((case) task) /////////////////////////////////////////////

  public void testCaseTaskDeleteCandidateGroup() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    // when
    taskService.deleteCandidateGroup(taskId, "accounting");

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // delete user identity link ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskDeleteUserIdentityLinkWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    try {
      // when
      taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to delete an user identity link");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDeleteUserIdentityLink() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDeleteUserIdentityLinkWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  // delete user identity link ((process) task) /////////////////////////////////////////////

  public void testProcessTaskDeleteUserIdentityLinkWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    try {
      // when
      taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to delete an user identity link");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskDeleteUserIdentityLinkWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteUserIdentityLinkWithTaskAssignPersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteUserIdentityLinkWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteUserIdentityLinkWithTaskAssignPersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteUserIdentityLinkWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteUserIdentityLinkWithTaskAssignPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteUserIdentityLink() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteUserIdentityLinkWithTaskAssignPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // delete user identity link ((case) task) /////////////////////////////////////////////

  public void testCaseTaskDeleteUserIdentityLink() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    // when
    taskService.deleteUserIdentityLink(taskId, "demo", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // delete group identity link ((standalone) task) /////////////////////////////////////////////

  public void testStandaloneTaskDeleteGroupIdentityLinkWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateGroup(taskId, "accounting");

    try {
      // when
      taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to delete a group identity link");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDeleteGroupIdentityLink() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  // delete group identity link ((process) task) /////////////////////////////////////////////

  public void testProcessTaskDeleteGroupIdentityLinkWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    try {
      // when
      taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);
      fail("Exception expected: It should not be possible to delete a group identity link");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskDeleteGroupIdentityLinkWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteGroupIdentityLinkWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteGroupIdentityLinkWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  public void testProcessTaskDeleteGroupIdentityLink() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // delete group identity link ((case) task) /////////////////////////////////////////////

  public void testCaseTaskDeleteGroupIdentityLink() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateGroup(taskId, "accounting");

    // when
    taskService.deleteGroupIdentityLink(taskId, "accounting", IdentityLinkType.CANDIDATE);

    // then
    disableAuthorization();
    List<IdentityLink> linksForTask = taskService.getIdentityLinksForTask(taskId);
    enableAuthorization();

    assertNotNull(linksForTask);
    assertTrue(linksForTask.isEmpty());
  }

  // get identity links ((standalone) task) ////////////////////////////////////////////////

  public void testStandaloneTaskGetIdentityLinksWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    try {
      // when
      taskService.getIdentityLinksForTask(taskId);
      fail("Exception expected: It should not be possible to get identity links");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have 'READ' permission on resource 'myTask' of type 'Task'", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskGetIdentityLinks() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, READ);

    // when
    List<IdentityLink> identityLinksForTask = taskService.getIdentityLinksForTask(taskId);

    // then
    assertNotNull(identityLinksForTask);
    assertFalse(identityLinksForTask.isEmpty());

    deleteTask(taskId, true);
  }

  // get identity links ((process) task) ////////////////////////////////////////////////

  public void testProcessTaskGetIdentityLinksWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    try {
      // when
      taskService.getIdentityLinksForTask(taskId);
      fail("Exception expected: It should not be possible to get the identity links");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(READ.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(READ_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskGetIdentityLinksWithReadPersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, READ);

    // when
    List<IdentityLink> identityLinksForTask = taskService.getIdentityLinksForTask(taskId);

    // then
    assertNotNull(identityLinksForTask);
    assertFalse(identityLinksForTask.isEmpty());
  }

  public void testProcessTaskGetIdentityLinksWithReadPersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, ANY, userId, READ);

    // when
    List<IdentityLink> identityLinksForTask = taskService.getIdentityLinksForTask(taskId);

    // then
    assertNotNull(identityLinksForTask);
    assertFalse(identityLinksForTask.isEmpty());
  }

  public void testProcessTaskGetIdentityLinksWithReadTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK);

    // when
    List<IdentityLink> identityLinksForTask = taskService.getIdentityLinksForTask(taskId);

    // then
    assertNotNull(identityLinksForTask);
    assertFalse(identityLinksForTask.isEmpty());
  }

  public void testProcessTaskGetIdentityLinks() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, READ);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK);

    // when
    List<IdentityLink> identityLinksForTask = taskService.getIdentityLinksForTask(taskId);

    // then
    assertNotNull(identityLinksForTask);
    assertFalse(identityLinksForTask.isEmpty());
  }

  // get identity links ((case) task) ////////////////////////////////////////////////

  public void testCaseTaskGetIdentityLinks() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    addCandidateUser(taskId, "demo");

    // when
    List<IdentityLink> identityLinksForTask = taskService.getIdentityLinksForTask(taskId);

    // then
    assertNotNull(identityLinksForTask);
    assertFalse(identityLinksForTask.isEmpty());
  }

  // claim (standalone) task ////////////////////////////////////////////////////////////

  public void testStandaloneTaskClaimTaskWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.claim(taskId, "demo");
      fail("Exception expected: It should not be possible to claim the task.");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions:", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskClaimTask() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskClaimTaskWithTaskWorkPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskClaimTaskWithRevokeTaskWorkPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createRevokeAuthorization(TASK, taskId, userId, TASK_WORK);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    try {
      // when
      taskService.claim(taskId, "demo");
      fail("Exception expected: It should not be possible to complete a task");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_WORK", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  // claim (process) task ////////////////////////////////////////////////////////////

  public void testProcessTaskClaimTaskWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.claim(taskId, "demo");
      fail("Exception expected: It should not be possible to claim the task");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskClaimTaskWithUpdatePermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskClaimTaskWithTaskWorkPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskClaimTaskWithGrantTaskWorkAndRevokeUpdatePermissionsOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);
    createRevokeAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskClaimTaskWithRevokeTaskWorkPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createRevokeAuthorization(TASK, taskId, userId, TASK_WORK);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    try {
      // when
      taskService.complete(taskId);
      fail("Exception expected: It should not be possible to complete a task");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_WORK", e.getMessage());
    }

  }

  public void testProcessTaskClaimTaskWithUpdatePermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskClaimTaskWithTaskWorkPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskClaimTaskWithUpdateTasksPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskClaimTaskWithTaskWorkPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_WORK);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

  }

  public void testProcessTaskClaimTaskWithRevokeTaskWorkPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createRevokeAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_WORK);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    try {
      // when
      taskService.complete(taskId);
      fail("Exception expected: It should not be possible to complete a task");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_WORK", e.getMessage());
    }

  }

  public void testProcessTaskClaimTask() {
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // claim (case) task ////////////////////////////////////////////////////////////

  public void testCaseTaskClaimTask() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.claim(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // complete (standalone) task ////////////////////////////////////////////////////////////

  public void testStandaloneTaskCompleteTaskWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.complete(taskId);
      fail("Exception expected: It should not be possible to complete a task");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_WORK", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskCompleteTask() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);

    if (!processEngineConfiguration.getHistoryLevel().equals(HistoryLevel.HISTORY_LEVEL_NONE)) {
      historyService.deleteHistoricTaskInstance(taskId);
    }
  }

  public void testStandaloneTaskCompleteWithTaskWorkPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);

    if (!processEngineConfiguration.getHistoryLevel().equals(HistoryLevel.HISTORY_LEVEL_NONE)) {
      historyService.deleteHistoricTaskInstance(taskId);
    }
  }

  // complete (process) task ////////////////////////////////////////////////////////////

  public void testProcessTaskCompleteTaskWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.complete(taskId);
      fail("Exception expected: It should not be possible to complete a task");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskCompleteTaskWithUpdatePermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);
  }

  public void testProcessTaskCompleteTaskWithTaskWorkPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);
  }

  public void testProcessTaskCompleteTaskWithUpdatePermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);
  }

  public void testProcessTaskCompleteTaskWithUpdateTasksPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);
  }

  public void testProcessTaskCompleteTaskWithTaskWorkPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_WORK);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);
  }

  public void testProcessTaskCompleteTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);
  }

  // complete (case) task ////////////////////////////////////////////////////////////

  public void testCaseTaskCompleteTask() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.complete(taskId);

    // then
    Task task = selectSingleTask();
    assertNull(task);
  }

  // delegate (standalone) task ///////////////////////////////////////////////////////

  public void testStandaloneTaskDelegateTaskWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.delegateTask(taskId, "demo");
      fail("Exception expected: It should not be possible to delegate a task");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDelegateTask() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskDelegateTaskWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());

    deleteTask(taskId, true);
  }

  // delegate (process) task ///////////////////////////////////////////////////////////

  public void testProcessTaskDelegateTaskWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.delegateTask(taskId, "demo");
      fail("Exception expected: It should not be possible to delegate a task");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskDelegateTaskWithUpdatePermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskDelegateTaskWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskDelegateTaskWithUpdatePermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskDelegateTaskWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskDelegateTaskWithUpdateTasksPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskDelegateTaskWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskDelegateTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  public void testProcessTaskDelegateTaskWithTaskAssignPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // delegate (case) task /////////////////////////////////////////////////////////////////

  public void testCaseTaskDelegateTask() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.delegateTask(taskId, "demo");

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals("demo", task.getAssignee());
  }

  // resolve (standalone) task ///////////////////////////////////////////////////////

  public void testStandaloneTaskResolveTaskWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.resolveTask(taskId);
      fail("Exception expected: It should not be possible to resolve a task");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_WORK", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskResolveTask() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    setAssignee(taskId, userId);
    delegateTask(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.resolveTask(taskId);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(userId, task.getAssignee());

    deleteTask(taskId, true);
  }

  // delegate (process) task ///////////////////////////////////////////////////////////

  public void testProcessTaskResolveTaskWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.resolveTask(taskId);
      fail("Exception expected: It should not be possible to resolve a task");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskResolveTaskWithUpdatePermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    setAssignee(taskId, userId);
    delegateTask(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.resolveTask(taskId);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(userId, task.getAssignee());
  }

  public void testProcessTaskResolveTaskWithUpdatePermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    setAssignee(taskId, userId);
    delegateTask(taskId, "demo");

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.resolveTask(taskId);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(userId, task.getAssignee());
  }

  public void testProcessTaskResolveTaskWithUpdateTasksPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    setAssignee(taskId, userId);
    delegateTask(taskId, "demo");

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.resolveTask(taskId);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(userId, task.getAssignee());
  }

  public void testProcessTaskResolveTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    setAssignee(taskId, userId);
    delegateTask(taskId, "demo");

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.resolveTask(taskId);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(userId, task.getAssignee());
  }

  // delegate (case) task /////////////////////////////////////////////////////////////////

  public void testCaseTaskResolveTask() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    setAssignee(taskId, userId);
    delegateTask(taskId, "demo");

    // when
    taskService.resolveTask(taskId);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(userId, task.getAssignee());
  }

  // set priority on standalone task /////////////////////////////////////////////

  public void testStandaloneTaskSetPriorityWithoutAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    try {
      // when
      taskService.setPriority(taskId, 80);
      fail("Exception expected: It should not be possible to set a priority");
    } catch (AuthorizationException e) {
      // then
      testHelper.assertTextPresent("The user with id 'test' does not have one of the following permissions: 'TASK_ASSIGN'", e.getMessage());
    }

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetPriority() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetPriorityWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());

    deleteTask(taskId, true);
  }

  // set priority on process task /////////////////////////////////////////////

  public void testProcessTaskSetPriorityWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    try {
      // when
      taskService.setPriority(taskId, 80);
      fail("Exception expected: It should not be possible to set a priority");
    } catch (AuthorizationException e) {
      // then
      String message = e.getMessage();
      testHelper.assertTextPresent(userId, message);
      testHelper.assertTextPresent(UPDATE.getName(), message);
      testHelper.assertTextPresent(taskId, message);
      testHelper.assertTextPresent(TASK.resourceName(), message);
      testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
      testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
    }
  }

  public void testProcessTaskSetPriorityWithUpdatePersmissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  public void testProcessTaskSetPriorityWithTaskAssignPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  public void testProcessTaskSetPriorityWithUpdatePersmissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, UPDATE);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  public void testProcessTaskSetPriorityWithTaskAssignPermissionOnAnyTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, ANY, userId, TASK_ASSIGN);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  public void testProcessTaskSetPriorityWithUpdateTasksPersmissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  public void testProcessTaskSetPriorityWithTaskAssignPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  public void testProcessTaskSetPriority() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  public void testProcessTaskSetPriorityWithTaskAssignPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_ASSIGN);

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  // set priority on case task /////////////////////////////////////////////

  public void testCaseTaskSetPriority() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    // when
    taskService.setPriority(taskId, 80);

    // then
    Task task = selectSingleTask();
    assertNotNull(task);
    assertEquals(80, task.getPriority());
  }

  // get sub tasks ((standalone) task) ////////////////////////////////////

  public void testStandaloneTaskGetSubTasksWithoutAuthorization() {
    // given
    String parentTaskId = "parentTaskId";
    createTask(parentTaskId);

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertTrue(subTasks.isEmpty());

    deleteTask(parentTaskId, true);
  }

  public void testStandaloneTaskGetSubTasksWithReadPermissionOnSub1() {
    // given
    String parentTaskId = "parentTaskId";
    createTask(parentTaskId);

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    createGrantAuthorization(TASK, "sub1", userId, READ);

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertFalse(subTasks.isEmpty());
    assertEquals(1, subTasks.size());

    assertEquals("sub1", subTasks.get(0).getId());

    deleteTask(parentTaskId, true);
  }

  public void testStandaloneTaskGetSubTasks() {
    // given
    String parentTaskId = "parentTaskId";
    createTask(parentTaskId);

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    createGrantAuthorization(TASK, ANY, userId, READ);

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertFalse(subTasks.isEmpty());
    assertEquals(2, subTasks.size());

    deleteTask(parentTaskId, true);
  }

  // get sub tasks ((process) task) ////////////////////////////////////

  public void testProcessTaskGetSubTasksWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String parentTaskId = selectSingleTask().getId();

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertTrue(subTasks.isEmpty());
  }

  public void testProcessTaskGetSubTasksWithReadPermissionOnSub1() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String parentTaskId = selectSingleTask().getId();

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    createGrantAuthorization(TASK, "sub1", userId, READ);

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertFalse(subTasks.isEmpty());
    assertEquals(1, subTasks.size());

    assertEquals("sub1", subTasks.get(0).getId());
  }

  public void testProcessTaskGetSubTasks() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String parentTaskId = selectSingleTask().getId();

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    createGrantAuthorization(TASK, ANY, userId, READ);

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertFalse(subTasks.isEmpty());
    assertEquals(2, subTasks.size());
  }

  // get sub tasks ((case) task) ////////////////////////////////////

  public void testCaseTaskGetSubTasksWithoutAuthorization() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String parentTaskId = selectSingleTask().getId();

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertTrue(subTasks.isEmpty());
  }

  public void testCaseTaskGetSubTasksWithReadPermissionOnSub1() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String parentTaskId = selectSingleTask().getId();

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    createGrantAuthorization(TASK, "sub1", userId, READ);

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertFalse(subTasks.isEmpty());
    assertEquals(1, subTasks.size());

    assertEquals("sub1", subTasks.get(0).getId());
  }

  public void testCaseTaskGetSubTasks() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String parentTaskId = selectSingleTask().getId();

    disableAuthorization();
    Task sub1 = taskService.newTask("sub1");
    sub1.setParentTaskId(parentTaskId);
    taskService.saveTask(sub1);

    Task sub2 = taskService.newTask("sub2");
    sub2.setParentTaskId(parentTaskId);
    taskService.saveTask(sub2);
    enableAuthorization();

    createGrantAuthorization(TASK, ANY, userId, READ);

    // when
    List<Task> subTasks = taskService.getSubTasks(parentTaskId);

    // then
    assertFalse(subTasks.isEmpty());
    assertEquals(2, subTasks.size());
  }

  // clear authorization ((standalone) task) ////////////////////////

  public void testStandaloneTaskClearAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .resourceId(taskId)
        .singleResult();
    enableAuthorization();
    assertNotNull(authorization);

    // when
    taskService.complete(taskId);

    // then
    disableAuthorization();
    authorization = authorizationService
        .createAuthorizationQuery()
        .resourceId(taskId)
        .singleResult();
    enableAuthorization();

    assertNull(authorization);

    deleteTask(taskId, true);
  }

  // clear authorization ((process) task) ////////////////////////

  public void testProcessTaskClearAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .resourceId(taskId)
        .singleResult();
    enableAuthorization();
    assertNotNull(authorization);

    // when
    taskService.complete(taskId);

    // then
    disableAuthorization();
    authorization = authorizationService
        .createAuthorizationQuery()
        .resourceId(taskId)
        .singleResult();
    enableAuthorization();

    assertNull(authorization);
  }

  // set assignee -> an authorization is available (standalone task) /////////////////////////////////////////

  public void testStandaloneTaskSetAssigneeCreateNewAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetAssigneeUpdateAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetAssigneeToNullAuthorizationStillAvailable() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // set assignee to demo -> an authorization for demo is available
    taskService.setAssignee(taskId, "demo");

    // when
    taskService.setAssignee(taskId, null);

    // then
    // authorization for demo is still available
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testQueryStandaloneTaskSetAssignee() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // set assignee to demo -> an authorization for demo is available
    taskService.setAssignee(taskId, "demo");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetAssigneeOutsideCommandContextInsert() {
    // given
    String taskId = "myTask";
    createGrantAuthorization(TASK, ANY, userId, CREATE);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    Task task = taskService.newTask(taskId);
    task.setAssignee("demo");

    // when
    taskService.saveTask(task);

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetAssigneeOutsideCommandContextSave() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    Task task = selectSingleTask();

    task.setAssignee("demo");

    // when
    taskService.saveTask(task);

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  // set assignee -> an authorization is available (process task) /////////////////////////////////////////

  public void testProcessTaskSetAssigneeCreateNewAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testProcessTaskSetAssigneeUpdateAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testProcessTaskSetAssigneeToNullAuthorizationStillAvailable() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // set assignee to demo -> an authorization for demo is available
    taskService.setAssignee(taskId, "demo");

    // when
    taskService.setAssignee(taskId, null);

    // then
    // authorization for demo is still available
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testQueryProcessTaskSetAssignee() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // set assignee to demo -> an authorization for demo is available
    taskService.setAssignee(taskId, "demo");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
  }

  public void testProcessTaskAssignee() {
    // given
    createGrantAuthorization(PROCESS_DEFINITION, DEMO_ASSIGNEE_PROCESS_KEY, userId, CREATE_INSTANCE);
    createGrantAuthorization(PROCESS_INSTANCE, ANY, userId, CREATE);

    // when
    runtimeService.startProcessInstanceByKey(DEMO_ASSIGNEE_PROCESS_KEY);

    // then
    // an authorization for demo has been created
    String taskId = selectSingleTask().getId();
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    // demo is able to retrieve the task
    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    Task task = taskService.createTaskQuery().singleResult();

    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
  }

  // set assignee -> should not create an authorization (case task) /////////////////////////////////////////

  public void testCaseTaskSetAssigneeNoAuthorization() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setAssignee(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNull(authorization);
  }

  // set owner -> an authorization is available (standalone task) /////////////////////////////////////////

  public void testStandaloneTaskSetOwnerCreateNewAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetOwnerUpdateAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testQueryStandaloneTaskSetOwner() {
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // set owner to demo -> an authorization for demo is available
    taskService.setOwner(taskId, "demo");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetOwnerOutsideCommandContextInsert() {
    // given
    String taskId = "myTask";
    createGrantAuthorization(TASK, ANY, userId, CREATE);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    Task task = taskService.newTask(taskId);
    task.setOwner("demo");

    // when
    taskService.saveTask(task);

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskSetOwnerOutsideCommandContextSave() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    Task task = selectSingleTask();

    task.setOwner("demo");

    // when
    taskService.saveTask(task);

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  // set owner -> an authorization is available (process task) /////////////////////////////////////////

  public void testProcessTaskSetOwnerCreateNewAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testProcessTaskSetOwnerUpdateAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testQueryProcessTaskSetOwner() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // set owner to demo -> an authorization for demo is available
    taskService.setOwner(taskId, "demo");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
  }

  // set owner -> should not create an authorization  (case task) /////////////////////////////////

  public void testCaseTaskSetOwnerNoAuthorization() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.setOwner(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNull(authorization);
  }

  // add candidate user -> an authorization is available (standalone task) /////////////////

  public void testStandaloneTaskAddCandidateUserCreateNewAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddCandidateUserUpdateAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testQueryStandaloneTaskAddCandidateUser() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // add candidate user -> an authorization for demo is available
    taskService.addCandidateUser(taskId, "demo");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
    deleteTask(taskId, true);
  }

  public void testQueryStandaloneTaskAddCandidateUserWithTaskAssignPermission() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, TASK_ASSIGN);

    // add candidate user -> an authorization for demo is available
    taskService.addCandidateUser(taskId, "demo");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
    deleteTask(taskId, true);
  }

  // add candidate user -> an authorization is available (process task) ////////////////////

  public void testProcessTaskAddCandidateUserCreateNewAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testProcessTaskAddCandidateUserUpdateAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testQueryProcessTaskAddCandidateUser() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // add candidate user -> an authorization for demo is available
    taskService.addCandidateUser(taskId, "demo");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
  }

  public void testProcessTaskCandidateUsers() {
    // given
    createGrantAuthorization(PROCESS_DEFINITION, CANDIDATE_USERS_PROCESS_KEY, userId, CREATE_INSTANCE);
    createGrantAuthorization(PROCESS_INSTANCE, ANY, userId, CREATE);

    // when
    runtimeService.startProcessInstanceByKey(CANDIDATE_USERS_PROCESS_KEY);

    // then
    // an authorization for demo has been created
    String taskId = selectSingleTask().getId();
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    // an authorization for test has been created
    disableAuthorization();
    authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("test")
        .resourceId(taskId)
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    // demo is able to retrieve the task
    identityService.clearAuthentication();
    identityService.setAuthentication("demo", null);

    Task task = taskService.createTaskQuery().singleResult();

    assertNotNull(task);
    assertEquals(taskId, task.getId());

    // test is able to retrieve the task
    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));

    task = taskService.createTaskQuery().singleResult();

    assertNotNull(task);
    assertEquals(taskId, task.getId());
  }

  // add candidate user -> should not create an authorization  (case task) /////////////////////////////////

  public void testCaseTaskAddCandidateUserNoAuthorization() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateUser(taskId, "demo");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .userIdIn("demo")
        .singleResult();
    enableAuthorization();

    assertNull(authorization);
  }

  // add candidate group -> an authorization is available (standalone task) /////////////////

  public void testStandaloneTaskAddCandidateGroupCreateNewAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateGroup(taskId, "management");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .groupIdIn("management")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testStandaloneTaskAddCandidateGroupUpdateAuthorization() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.addCandidateGroup(taskId, "management");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .groupIdIn("management")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    deleteTask(taskId, true);
  }

  public void testQueryStandaloneTaskAddCandidateGroup() {
    // given
    String taskId = "myTask";
    createTask(taskId);

    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // add candidate group -> an authorization for group management is available
    taskService.addCandidateGroup(taskId, "management");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", Arrays.asList("management"));

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
    deleteTask(taskId, true);
  }

  // add candidate group -> an authorization is available (process task) ////////////////////

  public void testProcessTaskAddCandidateGroupCreateNewAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateGroup(taskId, "management");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .groupIdIn("management")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testProcessTaskAddCandidateGroupUpdateAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();

    createGrantAuthorization(TASK, taskId, userId, UPDATE);
    createGrantAuthorization(TASK, taskId, "demo", DELETE);

    // when
    taskService.addCandidateGroup(taskId, "management");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .groupIdIn("management")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));
  }

  public void testQueryProcessTaskAddCandidateGroup() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // add candidate group -> an authorization for group management is available
    taskService.addCandidateGroup(taskId, "management");

    identityService.clearAuthentication();
    identityService.setAuthentication("demo", Arrays.asList("management"));

    // when
    Task task = taskService.createTaskQuery().singleResult();

    // then
    assertNotNull(task);
    assertEquals(taskId, task.getId());

    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));
  }

  public void testProcessTaskCandidateGroups() {
    // given
    createGrantAuthorization(PROCESS_DEFINITION, CANDIDATE_GROUPS_PROCESS_KEY, userId, CREATE_INSTANCE);
    createGrantAuthorization(PROCESS_INSTANCE, ANY, userId, CREATE);

    // when
    runtimeService.startProcessInstanceByKey(CANDIDATE_GROUPS_PROCESS_KEY);

    // then
    // an authorization for management has been created
    String taskId = selectSingleTask().getId();
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .groupIdIn("management")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    // an authorization for accounting has been created
    disableAuthorization();
    authorization = authorizationService
        .createAuthorizationQuery()
        .groupIdIn("accounting")
        .singleResult();
    enableAuthorization();

    assertNotNull(authorization);
    assertEquals(TASK.resourceType(), authorization.getResourceType());
    assertEquals(taskId, authorization.getResourceId());
    assertTrue(authorization.isPermissionGranted(READ));
    assertTrue(authorization.isPermissionGranted(getDefaultTaskPermissionForUser()));

    // management is able to retrieve the task
    identityService.clearAuthentication();
    identityService.setAuthentication("demo", Arrays.asList("management"));

    Task task = taskService.createTaskQuery().singleResult();

    assertNotNull(task);
    assertEquals(taskId, task.getId());

    // accounting is able to retrieve the task
    identityService.clearAuthentication();
    identityService.setAuthentication(userId, Arrays.asList(groupId));

    task = taskService.createTaskQuery().singleResult();

    assertNotNull(task);
    assertEquals(taskId, task.getId());
  }

  // add candidate group -> should not create an authorization (case task) /////////////////////////////////

  public void testCaseTaskAddCandidateGroupNoAuthorization() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.addCandidateGroup(taskId, "management");

    // then
    disableAuthorization();
    Authorization authorization = authorizationService
        .createAuthorizationQuery()
        .groupIdIn("management")
        .singleResult();
    enableAuthorization();

    assertNull(authorization);
  }

  // TaskService#getVariable() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariable() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    // when
    Object variable = taskService.getVariable(taskId, VARIABLE_NAME);

    // then
    assertEquals(VARIABLE_VALUE, variable);
  }

  // TaskService#getVariableLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariableLocal() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariablesLocal(taskId, getVariables());
    enableAuthorization();

    // when
    Object variable = taskService.getVariableLocal(taskId, VARIABLE_NAME);

    // then
    assertEquals(VARIABLE_VALUE, variable);
  }

  // TaskService#getVariableTyped() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariableTyped() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    // when
    TypedValue typedValue = taskService.getVariableTyped(taskId, VARIABLE_NAME);

    // then
    assertNotNull(typedValue);
    assertEquals(VARIABLE_VALUE, typedValue.getValue());
  }

  // TaskService#getVariableLocalTyped() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariableLocalTyped() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariablesLocal(taskId, getVariables());
    enableAuthorization();

    // when
    TypedValue typedValue = taskService.getVariableLocalTyped(taskId, VARIABLE_NAME);

    // then
    assertNotNull(typedValue);
    assertEquals(VARIABLE_VALUE, typedValue.getValue());
  }

  // TaskService#getVariables() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariables() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    // when
    Map<String, Object> variables = taskService.getVariables(taskId);

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#getVariablesLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariablesLocal() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariablesLocal(taskId, getVariables());
    enableAuthorization();

    // when
    Map<String, Object> variables = taskService.getVariablesLocal(taskId);

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#getVariablesTyped() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariablesTyped() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    // when
    VariableMap variables = taskService.getVariablesTyped(taskId);

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#getVariablesLocalTyped() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariablesLocalTyped() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariablesLocal(taskId, getVariables());
    enableAuthorization();

    // when
    Map<String, Object> variables = taskService.getVariablesLocalTyped(taskId);

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#getVariables() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariablesByName() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    // when
    Map<String, Object> variables = taskService.getVariables(taskId, Arrays.asList(VARIABLE_NAME));

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#getVariablesLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariablesLocalByName() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariablesLocal(taskId, getVariables());
    enableAuthorization();

    // when
    Map<String, Object> variables = taskService.getVariablesLocal(taskId, Arrays.asList(VARIABLE_NAME));

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#getVariables() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariablesTypedByName() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    // when
    VariableMap variables = taskService.getVariablesTyped(taskId, Arrays.asList(VARIABLE_NAME), false);

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#getVariablesLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskGetVariablesLocalTypedByName() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariablesLocal(taskId, getVariables());
    enableAuthorization();

    // when
    Map<String, Object> variables = taskService.getVariablesLocalTyped(taskId, Arrays.asList(VARIABLE_NAME), false);

    // then
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

  // TaskService#setVariable() (case task) /////////////////////////////////////

  public void testCaseTaskSetVariable() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    verifySetVariable(taskId);
  }

  // TaskService#setVariableLocal() (case task) /////////////////////////////////////

  public void testCaseTaskSetVariableLocal() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    verifySetVariableLocal(taskId);
  }

  // TaskService#setVariables() (case task) /////////////////////////////////////

  public void testCaseTaskSetVariables() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    verifySetVariables(taskId);
  }

  // TaskService#setVariablesLocal() (case task) /////////////////////////////////////

  public void testCaseTaskSetVariablesLocal() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    verifySetVariablesLocal(taskId);
  }

  // TaskService#removeVariable() (case task) ////////////////////////////////////////////

  public void testCaseTaskRemoveVariable() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    verifyRemoveVariable(taskId);
  }

  // TaskService#removeVariableLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskRemoveVariableLocal() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariableLocal(taskId, VARIABLE_NAME, VARIABLE_VALUE);
    enableAuthorization();

    verifyRemoveVariableLocal(taskId);
  }

  // TaskService#removeVariables() (case task) ////////////////////////////////////////////

  public void testCaseTaskRemoveVariables() {
    // given
    createCaseInstanceByKey(CASE_KEY, getVariables());
    String taskId = selectSingleTask().getId();

    verifyRemoveVariables(taskId);
  }

  // TaskService#removeVariablesLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskRemoveVariablesLocal() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    disableAuthorization();
    taskService.setVariableLocal(taskId, VARIABLE_NAME, VARIABLE_VALUE);
    enableAuthorization();

    verifyRemoveVariablesLocal(taskId);
  }

  // TaskServiceImpl#updateVariablesLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskUpdateVariablesLocal() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    verifyUpdateVariablesLocal(taskId);
  }

  // TaskServiceImpl#updateVariablesLocal() (case task) ////////////////////////////////////////////

  public void testCaseTaskUpdateVariables() {
    // given
    createCaseInstanceByKey(CASE_KEY);
    String taskId = selectSingleTask().getId();

    verifyUpdateVariables(taskId);
  }

  public void testStandaloneTaskSaveWithGenericResourceIdOwner() {
    createGrantAuthorization(TASK, ANY, userId, CREATE);

    Task task = taskService.newTask();
    task.setOwner("*");

    try {
      taskService.saveTask(task);
      fail("it should not be possible to save a task with the generic resource id *");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot create default authorization for owner *: "
          + "id cannot be *. * is a reserved identifier", e.getMessage());
    }
  }

  public void testStandaloneTaskSaveWithGenericResourceIdOwnerTaskServiceApi() {
    createGrantAuthorization(TASK, ANY, userId, CREATE, UPDATE);

    Task task = taskService.newTask();
    taskService.saveTask(task);

    try {
      taskService.setOwner(task.getId(), "*");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot create default authorization for owner *: "
          + "id cannot be *. * is a reserved identifier", e.getMessage());
    }

    deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskSaveWithGenericResourceIdAssignee() {
    createGrantAuthorization(TASK, ANY, userId, CREATE);

    Task task = taskService.newTask();
    task.setAssignee("*");

    try {
      taskService.saveTask(task);
      fail("it should not be possible to save a task with the generic resource id *");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot create default authorization for assignee *: "
          + "id cannot be *. * is a reserved identifier", e.getMessage());
    }
  }

  public void testStandaloneTaskSaveWithGenericResourceIdAssigneeTaskServiceApi() {
    createGrantAuthorization(TASK, ANY, userId, CREATE, UPDATE);

    Task task = taskService.newTask();
    taskService.saveTask(task);

    try {
      taskService.setAssignee(task.getId(), "*");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot create default authorization for assignee *: "
          + "id cannot be *. * is a reserved identifier", e.getMessage());
    }

    deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskSaveIdentityLinkWithGenericUserId() {
    createGrantAuthorization(TASK, ANY, userId, CREATE, UPDATE);

    Task task = taskService.newTask();
    taskService.saveTask(task);

    try {
      taskService.addUserIdentityLink(task.getId(), "*", "someLink");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot grant default authorization for identity link to user *: "
          + "id cannot be *. * is a reserved identifier.", e.getMessage());
    }

    deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskSaveIdentityLinkWithGenericGroupId() {
    createGrantAuthorization(TASK, ANY, userId, CREATE, UPDATE);

    Task task = taskService.newTask();
    taskService.saveTask(task);

    try {
      taskService.addGroupIdentityLink(task.getId(), "*", "someLink");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot grant default authorization for identity link to group *: "
          + "id cannot be *. * is a reserved identifier.", e.getMessage());
    }

    deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskSaveIdentityLinkWithGenericGroupIdAndTaskAssignPermission() {
    createGrantAuthorization(TASK, ANY, userId, CREATE, TASK_ASSIGN);

    Task task = taskService.newTask();
    taskService.saveTask(task);

    try {
      taskService.addGroupIdentityLink(task.getId(), "*", "someLink");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot grant default authorization for identity link to group *: "
          + "id cannot be *. * is a reserved identifier.", e.getMessage());
    }

    deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskSaveIdentityLinkWithGenericTaskId() {
    createGrantAuthorization(TASK, ANY, userId, CREATE, UPDATE);

    Task task = taskService.newTask();
    taskService.saveTask(task);

    try {
      taskService.addUserIdentityLink("*", "aUserId", "someLink");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot find task with id *", e.getMessage());
    }

    try {
      taskService.addGroupIdentityLink("*", "aGroupId", "someLink");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot find task with id *", e.getMessage());
    }

    deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskSaveIdentityLinkWithGenericTaskIdAndTaskAssignPermission() {
    createGrantAuthorization(TASK, ANY, userId, CREATE, TASK_ASSIGN);

    Task task = taskService.newTask();
    taskService.saveTask(task);

    try {
      taskService.addUserIdentityLink("*", "aUserId", "someLink");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot find task with id *", e.getMessage());
    }

    try {
      taskService.addGroupIdentityLink("*", "aGroupId", "someLink");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot find task with id *", e.getMessage());
    }

    deleteTask(task.getId(), true);
  }

  @Deployment
  public void testSetGenericResourceIdAssignee() {
    createGrantAuthorization(Resources.PROCESS_DEFINITION, Authorization.ANY, userId, CREATE_INSTANCE);
    createGrantAuthorization(Resources.PROCESS_INSTANCE, Authorization.ANY, userId, CREATE);

    try {
      runtimeService.startProcessInstanceByKey("genericResourceIdAssignmentProcess");
      fail("exception expected");
    } catch (ProcessEngineException e) {
      testHelper.assertTextPresent("Cannot create default authorization for assignee *: "
          + "id cannot be *. * is a reserved identifier.", e.getMessage());
    }
  }

  public void testAssignSameAssigneeAndOwnerToTask() {

    // given
    createGrantAuthorization(Resources.TASK, Authorization.ANY, userId, Permissions.ALL);

    // when
    Task newTask = taskService.newTask();
    newTask.setAssignee("Horst");
    newTask.setOwner("Horst");

    // then
    try {
      taskService.saveTask(newTask);
    } catch (Exception e) {
      fail("Setting same assignee and owner to user should not fail!");
    }

    taskService.deleteTask(newTask.getId(), true);
  }

  public void testPermissionsOnAssignSameAssigneeAndOwnerToTask() {

    try {
      // given
      createGrantAuthorization(Resources.TASK, Authorization.ANY, userId, Permissions.CREATE, Permissions.DELETE, Permissions.READ);
      processEngineConfiguration.setResourceAuthorizationProvider(new MyExtendedPermissionDefaultAuthorizationProvider());

      // when
      Task newTask = taskService.newTask();
      newTask.setAssignee("Horst");
      newTask.setOwner("Horst");
      taskService.saveTask(newTask);

      // then
      Authorization auth = authorizationService.createAuthorizationQuery().userIdIn("Horst").singleResult();
      assertTrue(auth.isPermissionGranted(Permissions.DELETE));

      taskService.deleteTask(newTask.getId(), true);

    } finally {
      processEngineConfiguration.setResourceAuthorizationProvider(new DefaultAuthorizationProvider());
    }


  }

  @Deployment
  public void testAssignSameAssigneeAndOwnerToProcess() {
    //given
    createGrantAuthorization(Resources.PROCESS_DEFINITION, Authorization.ANY, userId, Permissions.ALL);
    createGrantAuthorization(Resources.PROCESS_INSTANCE, Authorization.ANY, userId, Permissions.ALL);

    // when
    runtimeService.startProcessInstanceByKey("process");

    // then
    List<Authorization> auths = authorizationService.createAuthorizationQuery().userIdIn("horst").list();
    assertTrue(auths.size() == 1);

  }

  @Deployment
  public void testAssignSameUserToProcessTwice() {
    //given
    createGrantAuthorization(Resources.PROCESS_DEFINITION, Authorization.ANY, userId, Permissions.ALL);
    createGrantAuthorization(Resources.PROCESS_INSTANCE, Authorization.ANY, userId, Permissions.ALL);

    // when
    runtimeService.startProcessInstanceByKey("process");

    // then
    List<Authorization> auths = authorizationService.createAuthorizationQuery().userIdIn("hans").list();
    assertTrue(auths.size() == 1);
  }

  @Deployment
  public void testAssignSameGroupToProcessTwice() {
    //given
    createGrantAuthorization(Resources.PROCESS_DEFINITION, Authorization.ANY, userId, Permissions.ALL);
    createGrantAuthorization(Resources.PROCESS_INSTANCE, Authorization.ANY, userId, Permissions.ALL);

    // when
    runtimeService.startProcessInstanceByKey("process");

    // then
    List<Authorization> auths = authorizationService.createAuthorizationQuery().groupIdIn("abc").list();
    assertTrue(auths.size() == 1);
  }


  // helper ////////////////////////////////////////////////////////////////////////////////

  protected void verifyQueryResults(TaskQuery query, int countExpected) {
    verifyQueryResults((AbstractQuery<?, ?>) query, countExpected);
  }

  protected void verifyQueryResults(VariableInstanceQuery query, int countExpected) {
    verifyQueryResults((AbstractQuery<?, ?>) query, countExpected);
  }

  protected void verifyMessageIsValid(String taskId, String message) {
    testHelper.assertTextPresent(userId, message);
    testHelper.assertTextPresent(UPDATE.getName(), message);
    testHelper.assertTextPresent(UPDATE_VARIABLE.getName(), message);
    testHelper.assertTextPresent(taskId, message);
    testHelper.assertTextPresent(TASK.resourceName(), message);
    testHelper.assertTextPresent(UPDATE_TASK.getName(), message);
    testHelper.assertTextPresent(UPDATE_TASK_VARIABLE.getName(), message);
    testHelper.assertTextPresent(PROCESS_KEY, message);
    testHelper.assertTextPresent(PROCESS_DEFINITION.resourceName(), message);
  }

  protected void verifyVariableInstanceCountDisabledAuthorization(int count) {
    disableAuthorization();
    verifyQueryResults(runtimeService.createVariableInstanceQuery(), count);
    enableAuthorization();
  }

  protected void verifySetVariable(String taskId) {
    // when
    taskService.setVariable(taskId, VARIABLE_NAME, VARIABLE_VALUE);

    // then
    verifyVariableInstanceCountDisabledAuthorization(1);
  }

  protected void verifySetVariableLocal(String taskId) {
    // when
    taskService.setVariableLocal(taskId, VARIABLE_NAME, VARIABLE_VALUE);

    // then
    verifyVariableInstanceCountDisabledAuthorization(1);
  }

  protected void verifySetVariables(String taskId) {
    // when
    taskService.setVariables(taskId, getVariables());

    // then
    verifyVariableInstanceCountDisabledAuthorization(1);
  }

  protected void verifySetVariablesLocal(String taskId) {
    // when
    taskService.setVariablesLocal(taskId, getVariables());

    // then
    verifyVariableInstanceCountDisabledAuthorization(1);
  }

  protected void verifyRemoveVariable(String taskId) {
    // when
    taskService.removeVariable(taskId, VARIABLE_NAME);

    verifyVariableInstanceCountDisabledAuthorization(0);
  }

  protected void verifyRemoveVariableLocal(String taskId) {
    // when
    taskService.removeVariableLocal(taskId, VARIABLE_NAME);

    verifyVariableInstanceCountDisabledAuthorization(0);
  }

  protected void verifyRemoveVariables(String taskId) {
    // when
    taskService.removeVariables(taskId, Arrays.asList(VARIABLE_NAME));

    verifyVariableInstanceCountDisabledAuthorization(0);
  }

  protected void verifyRemoveVariablesLocal(String taskId) {
    // when
    taskService.removeVariablesLocal(taskId, Arrays.asList(VARIABLE_NAME));

    // then
    verifyVariableInstanceCountDisabledAuthorization(0);
  }

  protected void verifyUpdateVariables(String taskId) {
    // when (1)
    ((TaskServiceImpl) taskService).updateVariables(taskId, getVariables(), null);

    // then (1)
    verifyVariableInstanceCountDisabledAuthorization(1);

    // when (2)
    ((TaskServiceImpl) taskService).updateVariables(taskId, null, Arrays.asList(VARIABLE_NAME));

    // then (2)
    verifyVariableInstanceCountDisabledAuthorization(0);

    // when (3)
    ((TaskServiceImpl) taskService).updateVariables(taskId, getVariables(), Arrays.asList(VARIABLE_NAME));

    // then (3)
    verifyVariableInstanceCountDisabledAuthorization(0);
  }

  protected void verifyUpdateVariablesLocal(String taskId) {
    // when (1)
    ((TaskServiceImpl) taskService).updateVariablesLocal(taskId, getVariables(), null);

    // then (1)
    verifyVariableInstanceCountDisabledAuthorization(1);

    // when (2)
    ((TaskServiceImpl) taskService).updateVariablesLocal(taskId, null, Arrays.asList(VARIABLE_NAME));

    // then (2)
    verifyVariableInstanceCountDisabledAuthorization(0);

    // when (3)
    ((TaskServiceImpl) taskService).updateVariablesLocal(taskId, getVariables(), Arrays.asList(VARIABLE_NAME));

    // then (3)
    verifyVariableInstanceCountDisabledAuthorization(0);
  }

  protected void verifyGetVariables(Map<String, Object> variables) {
    assertNotNull(variables);
    assertFalse(variables.isEmpty());
    assertEquals(1, variables.size());

    assertEquals(VARIABLE_VALUE, variables.get(VARIABLE_NAME));
  }

}
