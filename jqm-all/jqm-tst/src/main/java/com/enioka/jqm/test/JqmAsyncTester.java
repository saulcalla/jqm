package com.enioka.jqm.test;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.hsqldb.Server;

import com.enioka.jqm.api.JobRequest;
import com.enioka.jqm.api.JqmClientFactory;
import com.enioka.jqm.api.Query;
import com.enioka.jqm.api.State;
import com.enioka.jqm.jpamodel.DeploymentParameter;
import com.enioka.jqm.jpamodel.GlobalParameter;
import com.enioka.jqm.jpamodel.JobDef;
import com.enioka.jqm.jpamodel.Node;
import com.enioka.jqm.jpamodel.Queue;
import com.enioka.jqm.tools.JqmEngineOperations;
import com.enioka.jqm.tools.Main;

/**
 * <strong>No one should ever use this class. This is for internal JQM use only and may change without notice</strong> <br>
 * <br>
 * An asynchronous tester for JQM payloads.<br>
 * Tester instances are not thread safe.
 * 
 * JNDI resources must be declared in an external XML file.<br>
 *
 */
public class JqmAsyncTester
{
    private Map<String, JqmEngineOperations> engines = new HashMap<String, JqmEngineOperations>();
    private Map<String, Node> nodes = new HashMap<String, Node>();
    private Map<String, Queue> queues = new HashMap<String, Queue>();

    private EntityManagerFactory emf = null;
    private EntityManager em = null;
    private Server s = null;

    private boolean hasStarted = false;

    ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTION
    ///////////////////////////////////////////////////////////////////////////

    public JqmAsyncTester()
    {
        // Ext dir
        File extDir = new File("./ext");
        if (!extDir.exists() && !extDir.mkdir())
        {
            throw new RuntimeException(new IOException("./ext directory does not exist and cannot create it"));
        }

        s = Common.createHsqlServer();
        s.start();

        emf = Persistence.createEntityManagerFactory("jobqueue-api-pu", Common.jpaProperties(s));
        em = emf.createEntityManager();

        Properties p2 = new Properties();
        p2.put("emf", emf);
        JqmClientFactory.setProperties(p2);
        Main.setEmf(emf);

        // Minimum parameters
        Main.main(new String[] { "-u" });
        em.getTransaction().begin();
        em.createQuery("DELETE FROM Queue").executeUpdate();
        em.getTransaction().commit();

        // Needed parameters
        addGlobalParameter("defaultConnection", "");
    }

    public static JqmAsyncTester create()
    {
        return new JqmAsyncTester();
    }

    /**
     * A helper method which creates a preset environment with a single node called 'node1' and a single queue named 'queue1' being polled
     * every 100ms by the node with at most 10 parallel running job instances..
     */
    public static JqmAsyncTester createSingleNodeOneQueue()
    {
        return new JqmAsyncTester().addNode("node1").addQueue("queue1").deployQueueToNode("queue1", 10, 100, "node1");
    }

    ///////////////////////////////////////////////////////////////////////////
    // TEST PREPARATION
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Create a new node. It is not started by this method.<br>
     * This must be called before starting the tester.
     * 
     * @param nodeName
     *            the name of the node. Must be unique.
     */
    public JqmAsyncTester addNode(String nodeName)
    {
        if (hasStarted)
        {
            throw new IllegalStateException("tester has already started");
        }

        File resDirectoryPath = Common.createTempDirectory();
        Node node = new Node();
        node.setDlRepo(resDirectoryPath.getAbsolutePath());
        node.setDns("test");
        node.setName(nodeName);
        node.setRepo(".");
        node.setTmpDirectory(resDirectoryPath.getAbsolutePath());
        node.setPort(12);
        node.setRootLogLevel("TRACE");

        em.getTransaction().begin();
        em.persist(node);
        em.getTransaction().commit();
        nodes.put(nodeName, node);

        return this;
    }

    /**
     * Create a new queue. After creation, it is not polled by any node - see {@link #deployQueueToNode(String, String, int, int)} for
     * this.<br>
     * The first queue created is considered to be the default queue.<br>
     * This must be called before starting the engines.
     * 
     * @param name
     *            must be unique.
     */
    public JqmAsyncTester addQueue(String name)
    {
        if (hasStarted)
        {
            throw new IllegalStateException("tester has already started");
        }

        Queue q = new Queue();
        q.setName(name);
        q.setDescription("test queue");
        if (queues.size() == 0)
        {
            q.setDefaultQueue(true);
        }

        em.getTransaction().begin();
        em.persist(q);
        em.getTransaction().commit();
        queues.put(name, q);

        return this;
    }

    /**
     * This must be called before starting the engines.
     */
    public JqmAsyncTester deployQueueToNode(String queueName, int maxJobsRunning, int pollingIntervallMs, String... nodeName)
    {
        if (hasStarted)
        {
            throw new IllegalStateException("tester has already started");
        }

        for (String name : nodeName)
        {
            DeploymentParameter dp = new DeploymentParameter();
            dp.setNbThread(maxJobsRunning);
            dp.setNode(nodes.get(name));
            dp.setPollingInterval(pollingIntervallMs);
            dp.setQueue(queues.get(queueName));

            em.getTransaction().begin();
            em.persist(dp);
            em.getTransaction().commit();
        }

        return this;
    }

    /**
     * This can be called at any time (even after engine start).
     */
    public JqmAsyncTester addJobDefinition(TestJobDefinition description)
    {
        JobDef jd = Common.createJobDef(description, queues);
        em.getTransaction().begin();
        em.persist(jd);
        em.getTransaction().commit();

        return this;
    }

    /**
     * Sets or update a global parameter
     */
    public void addGlobalParameter(String key, String value)
    {
        em.getTransaction().begin();
        int i = em.createQuery("UPDATE GlobalParameter p SET p.value = :val WHERE p.key = :key").setParameter("key", key)
                .setParameter("val", value).executeUpdate();

        if (i == 0)
        {
            GlobalParameter gp = new GlobalParameter();
            gp.setKey(key);
            gp.setValue(value);
            em.persist(gp);
        }
        em.getTransaction().commit();
    }

    ///////////////////////////////////////////////////////////////////////////
    // DURING TEST
    ///////////////////////////////////////////////////////////////////////////

    /**
     * This actually starts the different engines configured with {@link #addNode(String)}.<br>
     * This can only be called once.
     */
    public JqmAsyncTester start()
    {
        if (hasStarted)
        {
            throw new IllegalStateException("cannot start twice");
        }
        hasStarted = true;
        for (Node n : nodes.values())
        {
            engines.put(n.getName(), Main.startEngine(n.getName()));
        }
        return this;
    }

    /**
     * Helper method to enqueue a new launch request. Simple JqmClientFactory.getClient().enqueue wrapper.
     * 
     * @return the request ID.
     */
    public int enqueue(String name)
    {
        return JqmClientFactory.getClient().enqueue(JobRequest.create(name, "test"));
    }

    /**
     * Wait for a given amount of results (OK or KO).
     * 
     * @param nbResult
     *            the expected result count
     * @param timeoutMs
     *            give up after this (throws a RuntimeException)
     * @param waitAdditionalMs
     *            after reaching the expected nbResult count, wait a little more (for example to ensure there is no additonal unwanted
     *            launch). Will usually be zero.
     */
    public void waitForResults(int nbResult, int timeoutMs, int waitAdditionalMs)
    {
        Calendar start = Calendar.getInstance();
        while (Query.create().run().size() < nbResult && Calendar.getInstance().getTimeInMillis() - start.getTimeInMillis() <= timeoutMs)
        {
            sleepms(100);
        }
        if (Query.create().run().size() < nbResult)
        {
            throw new RuntimeException("expected result count was not reached in specified timeout");
        }
        sleepms(waitAdditionalMs);
    }

    private void sleepms(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            // not an issue in tests
        }
    }

    /**
     * Stops all engines. Only returns when engines are fully stopped.
     */
    public void stop()
    {
        if (!hasStarted)
        {
            throw new IllegalStateException("cannot stop a tester which has not started");
        }
        for (JqmEngineOperations op : this.engines.values())
        {
            op.stop();
        }
        JqmClientFactory.resetClient();
        // No need to close EM & EMF - they were closed by the client reset.
        s.stop();
        waitDbStop();
        hasStarted = false;
        this.engines.clear();
    }

    private void waitDbStop()
    {
        while (s.getState() != 16)
        {
            this.sleepms(1);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // CLEANUP
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Removes all job instances from the queues and the history.
     * 
     * @param em
     */
    public void cleanupOperationalDbData()
    {
        em.getTransaction().begin();
        em.createQuery("DELETE Deliverable WHERE 1=1").executeUpdate();
        em.createQuery("DELETE Message WHERE 1=1").executeUpdate();
        em.createQuery("DELETE History WHERE 1=1").executeUpdate();
        em.createQuery("DELETE RuntimeParameter WHERE 1=1").executeUpdate();
        em.createQuery("DELETE JobInstance WHERE 1=1").executeUpdate();

        em.getTransaction().commit();
    }

    /**
     * Deletes all job definitions. This calls {@link #cleanupOperationalDbData()}
     * 
     * @param em
     */
    public void cleanupAllJobDefinitions()
    {
        cleanupOperationalDbData();

        em.getTransaction().begin();
        em.createQuery("DELETE JobDefParameter WHERE 1=1").executeUpdate();
        em.createQuery("DELETE JobDef WHERE 1=1").executeUpdate();
        em.getTransaction().commit();
    }

    ///////////////////////////////////////////////////////////////////////////
    // TEST RESULT ANALYSIS HELPERS
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Helper query (directly uses {@link Query}). Gives the count of all ended (KO and OK) job instances.
     */
    public int getHistoryAllCount()
    {
        return Query.create().run().size();
    }

    /**
     * Helper query (directly uses {@link Query}). Gives the count of all non-ended (waiting in queue, running...) job instances.
     */
    public int getQueueAllCount()
    {
        return Query.create().setQueryHistoryInstances(false).setQueryLiveInstances(true).run().size();
    }

    /**
     * Helper query (directly uses {@link Query}). Gives the count of all OK-ended job instances.
     */
    public int getOkCount()
    {
        return Query.create().addStatusFilter(State.ENDED).run().size();
    }

    /**
     * Helper query (directly uses {@link Query}). Gives the count of all non-OK-ended job instances.
     */
    public int getNonOkCount()
    {
        return Query.create().addStatusFilter(State.CRASHED).addStatusFilter(State.KILLED).run().size();
    }

    /**
     * Helper method. Tests if {@link #getOkCount()} is equal to the given parameter.
     */
    public boolean testOkCount(long expectedOkCount)
    {
        return getOkCount() == expectedOkCount;
    }

    /**
     * Helper method. Tests if {@link #getNonOkCount()} is equal to the given parameter.
     */
    public boolean testKoCount(long expectedKoCount)
    {
        return getNonOkCount() == expectedKoCount;
    }
}
