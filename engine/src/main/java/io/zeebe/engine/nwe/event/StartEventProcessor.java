/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe.event;

import io.zeebe.engine.nwe.BpmnElementContext;
import io.zeebe.engine.nwe.BpmnElementProcessor;
import io.zeebe.engine.nwe.behavior.BpmnBehaviors;
import io.zeebe.engine.nwe.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;

public class StartEventProcessor implements BpmnElementProcessor<ExecutableStartEvent> {

  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;

  public StartEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
  }

  @Override
  public Class<ExecutableStartEvent> getType() {
    return ExecutableStartEvent.class;
  }

  @Override
  public void onActivating(final ExecutableStartEvent element, final BpmnElementContext context) {
    // variable mapping if message event
  }

  @Override
  public void onActivated(final ExecutableStartEvent element, final BpmnElementContext context) {}

  @Override
  public void onCompleting(final ExecutableStartEvent element, final BpmnElementContext context) {
    // variable mapping if message event
  }

  @Override
  public void onCompleted(final ExecutableStartEvent element, final BpmnElementContext context) {}

  @Override
  public void onTerminating(final ExecutableStartEvent element, final BpmnElementContext context) {}

  @Override
  public void onTerminated(final ExecutableStartEvent element, final BpmnElementContext context) {
    // resolve incidents
  }

  @Override
  public void onEventOccurred(
      final ExecutableStartEvent element, final BpmnElementContext context) {
    eventSubscriptionBehavior.triggerStartEvent(context);
  }
}
