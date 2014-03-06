package com.enioka.jqm.api;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClientFactory implements IClientFactory
{
    private static Logger jqmlogger = LoggerFactory.getLogger(ClientFactory.class);
    private static JqmClient defaultClient;
    private static ConcurrentMap<String, JqmClient> clients = new ConcurrentHashMap<String, JqmClient>();

    @Override
    public JqmClient getClient()
    {
        return getClient(null, null, true);
    }

    @Override
    public JqmClient getClient(String name, Properties props, boolean cached)
    {
        if (props == null)
        {
            props = new Properties();
        }

        if (!cached)
        {
            return new HibernateClient(props);
        }

        synchronized (clients)
        {
            if (name == null)
            {
                if (defaultClient == null)
                {
                    jqmlogger.debug("creating default client");
                    defaultClient = new HibernateClient(props);
                }
                return defaultClient;
            }
            else
            {
                clients.putIfAbsent(name, new HibernateClient(props));
                return clients.get(name);
            }
        }
    }

    @Override
    public void resetClient(String name)
    {
        if (name != null)
        {
            synchronized (clients)
            {
                if (clients.containsKey(name))
                {
                    jqmlogger.debug("resetting client " + name);
                    clients.get(name).dispose();
                    clients.remove(name);
                }
            }
        }
        else
        {
            synchronized (clients)
            {
                if (defaultClient != null)
                {
                    jqmlogger.debug("resetting defauilt client");
                    defaultClient.dispose();
                    defaultClient = null;
                }
            }
        }
    }

}