package org.jbpm.process.longrest;

import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.internal.runtime.manager.context.CaseContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import static org.jbpm.process.longrest.LongRunningRestServiceWorkItemHandler.EXECUTE_REST_PROCESS_ID;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ExecuteRestProcessEventListener extends DefaultProcessEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteRestProcessEventListener.class);

//    private Map<String, Object> result = new HashMap<>();

//    public void afterVariableChanged(ProcessVariableChangedEvent event) {
//        System.out.println(">>>>>>>>>>>>>>>>>>> CHANGED " + event.getProcessInstance().getProcessId() + " - " + event.getVariableId() + " - " + event.getNewValue()); //TODO remove me
//        if ("result".equals(event.getVariableId())) {
//            logger.debug("Variable 'result' changed to: {}.", event.getNewValue());
//            result.clear();
//            result.putAll((Map<? extends String, ?>) event.getNewValue());
//        }
//    }


    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        org.jbpm.process.instance.ProcessInstance processInstance = (org.jbpm.process.instance.ProcessInstance) event.getProcessInstance();
        if (!processInstance.getProcessId().equals(EXECUTE_REST_PROCESS_ID)) {
            //TODO trace
            logger.debug("I'm interested only in {} process instances.", EXECUTE_REST_PROCESS_ID);
            return;
        }

        KieRuntime kruntime = processInstance.getKnowledgeRuntime();
        KieRuntime kruntimeForParent = kruntime;
        RuntimeManager manager = (RuntimeManager) kruntime.getEnvironment().get(EnvironmentName.RUNTIME_MANAGER);
        if (manager != null) {
            org.kie.api.runtime.manager.Context<?> context = ProcessInstanceIdContext.get(processInstance.getParentProcessInstanceId());
            String caseId = (String) kruntime.getEnvironment().get(EnvironmentName.CASE_ID);
            if (caseId != null) {
                context = CaseContext.get(caseId);
            }
            RuntimeEngine runtime = manager.getRuntimeEngine(context);
            kruntimeForParent = (KieRuntime) runtime.getKieSession();
        }

        long workItemId = (long) ((WorkflowProcessInstanceImpl) processInstance).getVariable("workItemId");
        Map<String, Object> result = Collections.singletonMap("result", ((WorkflowProcessInstanceImpl) processInstance).getVariable("result"));
        kruntimeForParent.getWorkItemManager().completeWorkItem(workItemId, result);
//TODO        logger.debug(formatProcessMessage("afterProcessCompleted", event));
        logger.debug("AfterProcessCompleted {}", event);
    }
}
