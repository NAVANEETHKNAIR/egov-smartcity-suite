/*
 * @(#)SimpleWorkflowService.java 3.0, 17 Jun, 2013 4:33:14 PM
 * Copyright 2013 eGovernments Foundation. All rights reserved.
 * eGovernments PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.egov.infstr.workflow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.egov.exceptions.EGOVRuntimeException;
import org.egov.infstr.models.Script;
import org.egov.infstr.models.StateAware;
import org.egov.infstr.services.PersistenceService;
import org.egov.infstr.services.ScriptService;
import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Restrictions;

/**
 * SimpleWorkflowService implements WorkflowService Used for Workflow state
 * transitions, supports script based & manual Workflow state transitions
 */
@SuppressWarnings("unchecked")
public class SimpleWorkflowService<T extends StateAware> implements WorkflowService<T> {

    private final PersistenceService<T, Long> stateAwarePersistenceService;
    private PersistenceService<Script, Long> scriptPersistenceService;
    private PersistenceService<Action, Long> actionPersistenceService;
    private ScriptService scriptExecutionService;

    public SimpleWorkflowService(final PersistenceService<T, Long> stateAwarePersistenceService) {
        this.stateAwarePersistenceService = stateAwarePersistenceService;
    }

    public void setScriptExecutionService(final ScriptService scriptExecutionService) {
        this.scriptExecutionService = scriptExecutionService;
    }

    public void setScriptPersistenceService(final PersistenceService<Script, Long> scriptPersistenceService) {
        this.scriptPersistenceService = scriptPersistenceService;
    }

    public void setActionPersistenceService(final PersistenceService<Action, Long> actionPersistenceService) {
        this.actionPersistenceService = actionPersistenceService;
    }

    @Override
    public T transition(final Action action, final T stateAware, final String comments) {
        scriptExecutionService.executeScript(getScript(stateAware, action.getName()), ScriptService.createContext("action", this,
                "wfItem", stateAware, "persistenceService", this.stateAwarePersistenceService, "workflowService", this,
                "comments", comments));
        return this.stateAwarePersistenceService.persist(stateAware);
    }

    @Override
    public T transition(final String actionName, final T stateAware, final String comment) {
        final List<Action> actions = this.actionPersistenceService.findAllByNamedQuery(Action.BY_NAME_AND_TYPE,
                actionName, stateAware.getStateType());
        Action action = new Action(actionName, stateAware.getStateType(), actionName);
        if (!actions.isEmpty())
            action = actions.get(0);
        return transition(action, stateAware, comment);
    }

    @Override
    public List<Action> getValidActions(final T stateAware) {
        final String scriptName = stateAware.getStateType() + ".workflow.validactions";
        final Script trasitionScript = this.scriptPersistenceService.findAllByNamedQuery(Script.BY_NAME, scriptName)
                .get(0);
        final List<String> actionNames = (List<String>) scriptExecutionService.executeScript(trasitionScript,
                ScriptService.createContext("wfItem", stateAware, "workflowService", this, "persistenceService",
                        this.stateAwarePersistenceService));
        final List<Action> savedActions = this.actionPersistenceService.findAllByNamedQuery(Action.IN_NAMES_AND_TYPE,
                stateAware.getStateType(), actionNames);
        return savedActions.isEmpty() ? createActions(stateAware, actionNames) : savedActions;
    }

    public Object execute(final T stateAware) {
        final Script script = getScript(stateAware, "");
        return scriptExecutionService.executeScript(script, ScriptService.createContext("action", this, "wfItem", stateAware,
                "persistenceService", this.stateAwarePersistenceService));
    }

    public Object execute(final T stateAware, final String comments) {
        final Script script = getScript(stateAware, "");
        return scriptExecutionService.executeScript(script, ScriptService.createContext("action", this, "wfItem", stateAware,
                "persistenceService", this.stateAwarePersistenceService, "comments", comments));
    }

    private Script getScript(final T stateAware, final String actionName) {
        List<Script> scripts = null;
        
        if(!actionName.isEmpty())
            scripts = scriptPersistenceService.findAllByNamedQuery(Script.BY_NAME, stateAware.getStateType()+ ".workflow." + actionName);
        
        if (scripts == null || scripts.isEmpty())
            scripts = scriptPersistenceService.findAllByNamedQuery(Script.BY_NAME, stateAware.getStateType()+ ".workflow");

        if (scripts == null || scripts.isEmpty())
            throw new EGOVRuntimeException("workflow.script.notfound");
        
        return scripts.get(0);
    }

    private List<Action> createActions(final T stateAware, final List<String> actionNames) {
        final List<Action> actions = new ArrayList<Action>();
        for (final String action : actionNames)
            actions.add(new Action(action, stateAware.getStateType(), action));
        return actions;
    }

    @Override
    public WorkFlowMatrix getWfMatrix(final String type, final String department, final BigDecimal amountRule,
            final String additionalRule, final String currentState, final String pendingActions) {
        final Criteria wfMatrixCriteria = createWfMatrixAdditionalCriteria(type, department, amountRule,
                additionalRule, currentState, pendingActions);

        return getWorkflowMatrixObj(type, additionalRule, currentState, pendingActions, wfMatrixCriteria);

    }

    @Override
    public WorkFlowMatrix getWfMatrix(final String type, final String department, final BigDecimal amountRule,
            final String additionalRule, final String currentState, final String pendingActions, Date date) {
        final Criteria wfMatrixCriteria = createWfMatrixAdditionalCriteria(type, department, amountRule,
                additionalRule, currentState, pendingActions);
        if (null == date)
            date = new Date();
        final Criterion crit1 = Restrictions.le("fromDate", date);
        final Criterion crit2 = Restrictions.ge("toDate", date);
        final Criterion crit3 = Restrictions.conjunction().add(crit1).add(crit2);
        wfMatrixCriteria.add(Restrictions.or(crit3, crit1));

        return getWorkflowMatrixObj(type, additionalRule, currentState, pendingActions, wfMatrixCriteria);

    }

    private WorkFlowMatrix getWorkflowMatrixObj(final String type, final String additionalRule,
            final String currentState, final String pendingActions, final Criteria wfMatrixCriteria) {
        final List<WorkFlowMatrix> objectTypeList = wfMatrixCriteria.list();

        if (objectTypeList.isEmpty()) {
            final Criteria defaulfWfMatrixCriteria = commonWorkFlowMatrixCriteria(type, additionalRule, currentState,
                    pendingActions);
            defaulfWfMatrixCriteria.add(Restrictions.eq("department", "ANY"));
            final List<WorkFlowMatrix> defaultObjectTypeList = defaulfWfMatrixCriteria.list();
            if (defaultObjectTypeList.isEmpty())
                return null;
            else
                return defaultObjectTypeList.get(0);
        } else {
            if (objectTypeList.size() > 0)
                for (final WorkFlowMatrix matrix : objectTypeList)
                    if (matrix.getToDate() == null)
                        return matrix;
            return objectTypeList.get(0);
        }
    }

    private Criteria createWfMatrixAdditionalCriteria(final String type, final String department,
            final BigDecimal amountRule, final String additionalRule, final String currentState,
            final String pendingActions) {
        final Criteria wfMatrixCriteria = commonWorkFlowMatrixCriteria(type, additionalRule, currentState,
                pendingActions);
        if (department != null && !"".equals(department.trim()))
            wfMatrixCriteria.add(Restrictions.eq("department", department));

        // Added restriction for amount rule
        if (amountRule != null && !BigDecimal.ZERO.equals(amountRule)) {
            final Criterion amount1st = Restrictions.conjunction().add(Restrictions.le("fromQty", amountRule))
                    .add(Restrictions.ge("toQty", amountRule));

            final Criterion amount2nd = Restrictions.conjunction().add(Restrictions.le("fromQty", amountRule))
                    .add(Restrictions.isNull("toQty"));
            wfMatrixCriteria.add(Restrictions.disjunction().add(amount1st).add(amount2nd));

        }
        return wfMatrixCriteria;
    }

    private Criteria commonWorkFlowMatrixCriteria(final String type, final String additionalRule,
            final String currentState, final String pendingActions) {

        final Criteria commonWfMatrixCriteria = this.stateAwarePersistenceService.getSession().createCriteria(
                WorkFlowMatrix.class);

        commonWfMatrixCriteria.add(Restrictions.eq("objectType", type));

        if (additionalRule != null && !"".equals(additionalRule.trim()))
            commonWfMatrixCriteria.add(Restrictions.eq("additionalRule", additionalRule));

        if (pendingActions != null && !"".equals(pendingActions.trim()))
            commonWfMatrixCriteria.add(Restrictions.ilike("pendingActions", pendingActions, MatchMode.ANYWHERE));

        if (currentState != null && !"".equals(currentState.trim()))
            commonWfMatrixCriteria.add(Restrictions.ilike("currentState", currentState, MatchMode.ANYWHERE));
        else
            commonWfMatrixCriteria.add(Restrictions.ilike("currentState", "NEW", MatchMode.ANYWHERE));

        return commonWfMatrixCriteria;
    }
}
