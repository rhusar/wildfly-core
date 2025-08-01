/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.controller.registry.AttributeAccess.Flag.COUNTER_METRIC;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.THREADING_PATH;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class ThreadResourceDefinition extends SimpleResourceDefinition {


    static AttributeDefinition CURRENT_THREAD_CPU_TIME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.CURRENT_THREAD_CPU_TIME, ModelType.LONG, false)
            .setMeasurementUnit(MeasurementUnit.NANOSECONDS)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    static AttributeDefinition CURRENT_THREAD_USER_TIME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.CURRENT_THREAD_USER_TIME, ModelType.LONG, false)
            .setMeasurementUnit(MeasurementUnit.NANOSECONDS)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();


    static AttributeDefinition ALL_THREAD_IDS = new PrimitiveListAttributeDefinition.Builder(PlatformMBeanConstants.ALL_THREAD_IDS, ModelType.LONG)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    static AttributeDefinition THREAD_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.THREAD_COUNT, ModelType.INT, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .build();

    static AttributeDefinition PEAK_THREAD_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.PEAK_THREAD_COUNT, ModelType.INT, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .build();

    static AttributeDefinition TOTAL_STARTED_THREAD_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.TOTAL_STARTED_THREAD_COUNT, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .setFlags(COUNTER_METRIC)
            .build();

    static AttributeDefinition DAEMON_THREAD_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.DAEMON_THREAD_COUNT, ModelType.INT, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .build();

    static AttributeDefinition THREAD_CONTENTION_MONITORING_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    static AttributeDefinition THREAD_CONTENTION_MONITORING_ENABLED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_ENABLED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    static AttributeDefinition THREAD_CPU_TIME_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.THREAD_CPU_TIME_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    static AttributeDefinition CURRENT_THREAD_CPU_TIME_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.CURRENT_THREAD_CPU_TIME_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    static AttributeDefinition THREAD_CPU_TIME_ENABLED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.THREAD_CPU_TIME_ENABLED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    static AttributeDefinition OBJECT_MONITOR_USAGE_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.OBJECT_MONITOR_USAGE_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    static AttributeDefinition SYNCHRONIZER_USAGE_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.SYNCHRONIZER_USAGE_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    static AttributeDefinition CURRENT_THREAD_ALLOCATED_BYTES = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.CURRENT_THREAD_ALLOCATED_BYTES, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setStability(Stability.COMMUNITY)
            .build();
    static AttributeDefinition THREAD_ALLOCATED_MEMORY_ENABLED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_ENABLED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    static AttributeDefinition THREAD_ALLOCATED_MEMORY_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();


    static final List<AttributeDefinition> METRICS = Arrays.asList(
            THREAD_COUNT,
            PEAK_THREAD_COUNT,
            TOTAL_STARTED_THREAD_COUNT,
            DAEMON_THREAD_COUNT,
            CURRENT_THREAD_CPU_TIME,
            CURRENT_THREAD_USER_TIME,
            CURRENT_THREAD_ALLOCATED_BYTES
    );
    static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            ALL_THREAD_IDS,
            THREAD_CONTENTION_MONITORING_SUPPORTED,
            THREAD_CPU_TIME_SUPPORTED,
            CURRENT_THREAD_CPU_TIME_SUPPORTED,
            OBJECT_MONITOR_USAGE_SUPPORTED,
            SYNCHRONIZER_USAGE_SUPPORTED,
            THREAD_ALLOCATED_MEMORY_SUPPORTED
    );
    static final List<AttributeDefinition> READ_WRITE_ATTRIBUTES = Arrays.asList(
            THREAD_CONTENTION_MONITORING_ENABLED,
            THREAD_CPU_TIME_ENABLED,
            THREAD_ALLOCATED_MEMORY_ENABLED
    );

    static final List<String> THREADING_READ_ATTRIBUTES = Arrays.asList(
            ALL_THREAD_IDS.getName(),
            THREAD_CONTENTION_MONITORING_SUPPORTED.getName(),
            THREAD_CPU_TIME_SUPPORTED.getName(),
            CURRENT_THREAD_CPU_TIME_SUPPORTED.getName(),
            OBJECT_MONITOR_USAGE_SUPPORTED.getName(),
            SYNCHRONIZER_USAGE_SUPPORTED.getName()
    );
    static final List<String> THREADING_EXTENDED_READ_ATTRIBUTES = Arrays.asList(
            THREAD_ALLOCATED_MEMORY_SUPPORTED.getName()
    );
    static final List<String> THREADING_METRICS = Arrays.asList(
            THREAD_COUNT.getName(),
            PEAK_THREAD_COUNT.getName(),
            TOTAL_STARTED_THREAD_COUNT.getName(),
            DAEMON_THREAD_COUNT.getName(),
            CURRENT_THREAD_CPU_TIME.getName(),
            CURRENT_THREAD_USER_TIME.getName()
    );
    static final List<String> THREADING_EXTENDED_METRICS = Arrays.asList(
            CURRENT_THREAD_ALLOCATED_BYTES.getName()
    );
    static final List<String> THREADING_READ_WRITE_ATTRIBUTES = Arrays.asList(
            THREAD_CONTENTION_MONITORING_ENABLED.getName(),
            THREAD_CPU_TIME_ENABLED.getName()
    );
    static final List<String> THREADING_EXTENDED_READ_WRITE_ATTRIBUTES = Arrays.asList(
            THREAD_ALLOCATED_MEMORY_ENABLED.getName()
    );

    static final ThreadResourceDefinition INSTANCE = new ThreadResourceDefinition();

    private ThreadResourceDefinition() {
        super(new Parameters(THREADING_PATH,
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, ThreadMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : READ_WRITE_ATTRIBUTES) {
            registration.registerReadWriteAttribute(attribute, ThreadMXBeanAttributeHandler.INSTANCE, ThreadMXBeanAttributeHandler.INSTANCE);
        }

        for (AttributeDefinition attribute : READ_ATTRIBUTES) {
            registration.registerReadOnlyAttribute(attribute, ThreadMXBeanAttributeHandler.INSTANCE);
        }

        for (AttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, ThreadMXBeanAttributeHandler.INSTANCE);
        }
    }

    static EnumSet<OperationEntry.Flag> READ_ONLY_RUNTIME_ONLY_FLAG = EnumSet.of(OperationEntry.Flag.RUNTIME_ONLY, OperationEntry.Flag.READ_ONLY);


    @Override
    public void registerOperations(ManagementResourceRegistration threads) {
        super.registerOperations(threads);
        threads.registerOperationHandler(ReadResourceHandler.DEFINITION, ThreadMXBeanReadResourceHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanResetPeakThreadCountHandler.DEFINITION, ThreadMXBeanResetPeakThreadCountHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanFindDeadlockedThreadsHandler.DEFINITION, ThreadMXBeanFindDeadlockedThreadsHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanFindMonitorDeadlockedThreadsHandler.DEFINITION, ThreadMXBeanFindMonitorDeadlockedThreadsHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanThreadInfoHandler.DEFINITION, ThreadMXBeanThreadInfoHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanThreadInfosHandler.DEFINITION, ThreadMXBeanThreadInfosHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanCpuTimeHandler.DEFINITION, ThreadMXBeanCpuTimeHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanUserTimeHandler.DEFINITION, ThreadMXBeanUserTimeHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanDumpAllThreadsHandler.DEFINITION, ThreadMXBeanDumpAllThreadsHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanCpuTimesHandler.DEFINITION, ThreadMXBeanCpuTimesHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanUserTimesHandler.DEFINITION, ThreadMXBeanUserTimesHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanThreadsAllocatedBytesHandler.DEFINITION, ThreadMXBeanThreadsAllocatedBytesHandler.INSTANCE);
        threads.registerOperationHandler(ThreadMXBeanThreadAllocatedBytesHandler.DEFINITION, ThreadMXBeanThreadAllocatedBytesHandler.INSTANCE);
    }
}

