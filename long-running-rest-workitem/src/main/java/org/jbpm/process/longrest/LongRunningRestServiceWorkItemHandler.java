/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
package org.jbpm.process.longrest;

import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.process.workitem.core.AbstractLogOrThrowWorkItemHandler;
import org.jbpm.process.workitem.core.util.Wid;
import org.jbpm.process.workitem.core.util.WidMavenDepends;
import org.jbpm.process.workitem.core.util.WidParameter;
import org.jbpm.process.workitem.core.util.WidResult;
import org.jbpm.process.workitem.core.util.service.WidAction;
import org.jbpm.process.workitem.core.util.service.WidService;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.internal.runtime.manager.context.CaseContext;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Wid(widfile="LongRunningRestService.wid",
        name="LongRunningRestService",
        displayName="LongRunningRestService",
        defaultHandler="mvel: new org.jbpm.contrib.longrest.LongRunningRestServiceWorkItemHandler(runtimeManager)",
        category="long-running-rest-workitem",
        documentation = "",
        parameters={
                @WidParameter(name="requestUrl", required = true),
                @WidParameter(name="requestMethod", required = true),
                @WidParameter(name="requestHeaders", required = false),
                @WidParameter(name="requestTemplate", required = false),
                @WidParameter(name="maxRetries", required = false),
                @WidParameter(name="retryDelay", required = false),

                @WidParameter(name="cancelUrlJsonPointer", required = false),
                @WidParameter(name="cancelUrlTemplate", required = false),
                @WidParameter(name="cancelMethod", required = false),
                @WidParameter(name="cancelHeaders", required = false),
                @WidParameter(name="cancelTimeout", required = false, runtimeType = "java.lang.Integer"),
                @WidParameter(name="ignoreCancelSignals", required = false, runtimeType = "java.lang.Boolean"),

                @WidParameter(name="successEvalTemplate", required = false),
                @WidParameter(name="noCallback", required = false, runtimeType = "java.lang.Boolean"),
                @WidParameter(name="taskTimeout", required = true, runtimeType = "java.lang.Integer"),
                @WidParameter(name="heartbeatTimeout", required = false),

                @WidParameter(name="cancel", required = false, runtimeType = "java.lang.Boolean")
        },
        results={
                @WidResult(name="result", runtimeType = "java.util.Map")
        },
        mavenDepends={
                @WidMavenDepends(group = "${groupId}", artifact = "${artifactId}", version = "${version}")
        },
        serviceInfo = @WidService(category = "REST service", description = "",
                keywords = "rest,long-running",
                action = @WidAction(title = "Long running REST service handler ver. ${version}")
        )
)
public class LongRunningRestServiceWorkItemHandler extends AbstractLogOrThrowWorkItemHandler {

    public static final String EXECUTE_REST_PROCESS_ID = "executerest";
    public static final String PARENT_PROCESS_INSTANCE_ID = "_parentProcessInstanceId";
    private static final Logger logger = LoggerFactory.getLogger(LongRunningRestServiceWorkItemHandler.class);
    private static final String EXECUTE_REST_INSTANCE_ID = "executeRestInstanceId";
    private final RuntimeManager runtimeManager;

    public LongRunningRestServiceWorkItemHandler(RuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
        logger.debug("Constructing with runtimeManager ...");

        setLogThrownException(false);
    }

    public LongRunningRestServiceWorkItemHandler() {
        logger.debug("Constructing without runtimeManager ...");
        runtimeManager = null;
        setLogThrownException(false);
    }

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        Map<String, Object> parameters = new HashMap<>(workItem.getParameters());
        //TODO if we manage to set process instance relations
        //link to parent process for variable resolution
//        parameters.put(PARENT_PROCESS_INSTANCE_ID, workItem.getProcessInstanceId());
        parameters.put("workItemId", workItem.getId());

        RuntimeEngine parentRuntimeEngine = runtimeManager.getRuntimeEngine(ProcessInstanceIdContext.get(workItem.getProcessInstanceId()));

        ProcessInstance parentProcessInstance = parentRuntimeEngine.getKieSession()
                .getProcessInstance(workItem.getProcessInstanceId());

        WorkItemNodeInstance workItemNodeInstance = getCurrentWorkItemNodeInstance(
                workItem,
                parentProcessInstance);

        KieRuntime kieSession = getKieRuntimeForSubprocess(parentProcessInstance);
        ProcessInstance processInstance = kieSession.createProcessInstance(EXECUTE_REST_PROCESS_ID, parameters);

        ((ProcessInstanceImpl) processInstance).setMetaData("ParentProcessInstanceId", workItem.getProcessInstanceId());
        ((ProcessInstanceImpl) processInstance).setMetaData("ParentNodeInstanceId", workItemNodeInstance.getUniqueId());

        ((ProcessInstanceImpl) processInstance).setParentProcessInstanceId(workItem.getProcessInstanceId());
        ((ProcessInstanceImpl) processInstance).setSignalCompletion(true);

        kieSession.startProcessInstance(processInstance.getId());
        logger.debug("Process started: {}({}).", processInstance.getProcessId(), processInstance.getId());

        runtimeManager.disposeRuntimeEngine(parentRuntimeEngine);
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(EmptyContext.get());
        runtimeEngine.getKieSession().abortProcessInstance((Long) workItem.getParameter(EXECUTE_REST_INSTANCE_ID));
        //TODO?
//        runtimeManager.disposeRuntimeEngine(runtimeEngine);
        completeWorkItem(manager, workItem.getId(), Collections.emptyMap(), Optional.of(new WorkitemAbortedException()));
    }

    private void completeWorkItem(
            WorkItemManager manager,
            long workItemId,
            Map<String, Object> result,
            Optional<Throwable> cause) {
        Map<String, Object> wihResult = new HashMap<>(result);
        cause.ifPresent(c -> {
            wihResult.put("error", c); //TODO exceptional end signal
        });
        logger.info("Long running rest service workitem completion result {}.", wihResult);
        manager.completeWorkItem(workItemId, wihResult);
    }

    private WorkItemNodeInstance getCurrentWorkItemNodeInstance(WorkItem workItem, ProcessInstance processInstance) {
        Collection<NodeInstance> nodeInstances = ((RuleFlowProcessInstance)processInstance).getNodeInstances();
        for(NodeInstance nodeInst : nodeInstances) {
            if(nodeInst instanceof WorkItemNodeInstance) {
                if(((WorkItemNodeInstance)nodeInst).getWorkItem().getId() == workItem.getId()) {
                    return ((WorkItemNodeInstance)nodeInst);
                }
            }
        }
        return null;
    }

    protected KieRuntime getKieRuntimeForSubprocess(ProcessInstance processInstance) {
        KieRuntime kruntime = ((WorkflowProcessInstance) processInstance).getKnowledgeRuntime();
        RuntimeManager manager = (RuntimeManager) kruntime.getEnvironment().get(EnvironmentName.RUNTIME_MANAGER);
        if (manager != null) {
            org.kie.api.runtime.manager.Context<?> context = ProcessInstanceIdContext.get();

            String caseId = (String) kruntime.getEnvironment().get(EnvironmentName.CASE_ID);
            if (caseId != null) {
                context = CaseContext.get(caseId);
            }

            RuntimeEngine runtime = manager.getRuntimeEngine(context);
            kruntime = (KieRuntime) runtime.getKieSession();
            runtimeManager.disposeRuntimeEngine(runtime);
        }

        return kruntime;
    }
}

