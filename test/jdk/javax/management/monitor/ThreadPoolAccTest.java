/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6222826
 * @summary Test that each thread in the thread pool runs
 *          in the context of the monitor.start() caller.
 * @author Luis-Miguel Alventosa
 *
 * @run clean ThreadPoolAccTest
 * @run build ThreadPoolAccTest
 *
 * @run main/othervm ThreadPoolAccTest
 * @run main/othervm -Djava.security.manager=allow ThreadPoolAccTest
 * @run main/othervm -Djava.security.manager=allow -DThreadPoolAccTest.useGetSubjectACC=true ThreadPoolAccTest
 * @run main/othervm/policy=all.policy ThreadPoolAccTest
 * @run main/othervm/policy=all.policy -DThreadPoolAccTest.useGetSubjectACC=true ThreadPoolAccTest
 */

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.monitor.CounterMonitor;
import javax.management.monitor.GaugeMonitor;
import javax.management.monitor.Monitor;
import javax.management.monitor.StringMonitor;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;

public class ThreadPoolAccTest {

    public static final int MAX_TRIES = 30;

    // MBean class
    public static class ObservedObject implements ObservedObjectMBean {
        public volatile String principal;
        public Integer getInteger() {
            setPrincipal();
            return 0;
        }
        public Double getDouble() {
            setPrincipal();
            return 0.0;
        }
        public String getString() {
            setPrincipal();
            return "";
        }
        private void setPrincipal() {
            // Use Subject.current() unless test Property is set.
            Subject subject = Boolean.getBoolean("ThreadPoolAccTest.useGetSubjectACC") ?
                              Subject.getSubject(AccessController.getContext()) : Subject.current();
            Set<JMXPrincipal> principals = subject.getPrincipals(JMXPrincipal.class);
            principal = principals.iterator().next().getName();
        }
    }

    // MBean interface
    public interface ObservedObjectMBean {
        public Integer getInteger();
        public Double getDouble();
        public String getString();
    }

    public static void main (String args[]) throws Exception {

        ObjectName[] mbeanNames = new ObjectName[6];
        ObservedObject[] monitored = new ObservedObject[6];
        ObjectName[] monitorNames = new ObjectName[6];
        Monitor[] monitor = new Monitor[6];
        String[] principals = { "role1", "role2" };
        String[] attributes = { "Integer", "Double", "String" };

        try {
            echo(">>> CREATE MBeanServer");
            MBeanServer server = MBeanServerFactory.newMBeanServer();

            for (int i = 0; i < 6; i++) {
                mbeanNames[i] =
                    new ObjectName(":type=ObservedObject,instance=" + i);
                monitored[i] = new ObservedObject();
                echo(">>> CREATE ObservedObject = " + mbeanNames[i].toString());
                server.registerMBean(monitored[i], mbeanNames[i]);

                switch (i) {
                    case 0:
                    case 3:
                        monitorNames[i] =
                            new ObjectName(":type=CounterMonitor,instance=" + i);
                        monitor[i] = new CounterMonitor();
                        break;
                    case 1:
                    case 4:
                        monitorNames[i] =
                            new ObjectName(":type=GaugeMonitor,instance=" + i);
                        monitor[i] = new GaugeMonitor();
                        break;
                    case 2:
                    case 5:
                        monitorNames[i] =
                            new ObjectName(":type=StringMonitor,instance=" + i);
                        monitor[i] = new StringMonitor();
                        break;
                }

                echo(">>> CREATE Monitor = " + monitorNames[i].toString());
                server.registerMBean(monitor[i], monitorNames[i]);
                monitor[i].addObservedObject(mbeanNames[i]);
                monitor[i].setObservedAttribute(attributes[i % 3]);
                monitor[i].setGranularityPeriod(500);
                final Monitor m = monitor[i];
                Subject subject = new Subject();
                echo(">>> RUN Principal = " + principals[i / 3]);
                subject.getPrincipals().add(new JMXPrincipal(principals[i / 3]));
                PrivilegedAction<Void> action = new PrivilegedAction<Void>() {
                    public Void run() {
                        m.start();
                        return null;
                    }
                };
                // Subject.doAs(subject, action);
                Callable<Void> c = (Callable<Void>) () -> action.run();
                Subject.callAs(subject, c);
            }

            sleep(500); // wait for getX method to be called, which calls setPrincipal
            boolean ok = false;
            for (int i = 0; i < MAX_TRIES; i++) {
                ok = testPrincipals(monitored, monitorNames, monitor, principals);
                if (ok) {
                    break;
                }
                sleep(1000);
            }
            if (!ok) {
                throw new Exception("Failed: testPrincipals fails repeatedly.");
            }
        } finally {
            for (int i = 0; i < 6; i++)
                if (monitor[i] != null)
                    monitor[i].stop();
        }
    }

    private static boolean testPrincipals(ObservedObject[] monitored, ObjectName[] monitorNames,
            Monitor[] monitor, String[] principals) throws Exception {
        for (int i = 0; i < 6; i++) {
            String principal =  monitored[i].principal;
            String expected = principals[i / 3];

            echo("testPrincipals: monitored: " + monitored[i] + " principal: " + principal + " expected: " + expected);

            if (principal == null) {
                echo("Task not submitted " + new Date() + ". RETRY");
                return false;
            }
            echo(">>> Monitor = " + monitorNames[i]);
            echo(">>> ObservedObject = " + monitor[i].getObservedObject());
            echo(">>> ObservedAttribute = " + monitor[i].getObservedAttribute());
            echo(">>> Principal = " + principal);

            if (expected.equals(principal)) {
                echo("\tOK: Got Expected principal");
            } else {
                throw new Exception("Unexpected principal. Got: " + principal + " Expected: " + expected);
            }
        }
        return true;
    }

    private static void echo(String message) {
        System.out.println(message);
    }

    public static void sleep(int ms) {
        try {
            Thread.currentThread().sleep(ms);
        } catch (InterruptedException ie) {
            // ignore
        }
   }
}
