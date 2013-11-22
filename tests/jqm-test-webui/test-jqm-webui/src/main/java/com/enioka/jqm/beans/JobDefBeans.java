package com.enioka.jqm.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.view.ViewScoped;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import com.enioka.jqm.api.Dispatcher;
import com.enioka.jqm.api.JobDefinition;
import com.enioka.jqm.jpamodel.JobDef;
import com.enioka.jqm.webui.JobDefCustom;

@ManagedBean
@ViewScoped
public class JobDefBeans implements Serializable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 2144227424128605638L;

	private ArrayList<JobDefCustom> jobs = new ArrayList<JobDefCustom>();
	//@ManagedProperty(value="#{jobDefBeans}")
	private List<com.enioka.jqm.jpamodel.JobDef> tmp;
	private List<JobDefCustom> willEnqueue;
	private JobDef j;
	private int cl;

	public JobDefBeans() {

		getJobs();
	}

	@PostConstruct
	public void initJobInstanceBeans()
	{
		EntityManagerFactory emf = Persistence.createEntityManagerFactory("jobqueue-api-pu");
		EntityManager em = emf.createEntityManager();

		System.out.println("ClassLoader Beans: " + JobDef.class.getClassLoader().hashCode());

		this.tmp = em.createQuery("SELECT j FROM JobDef j",
				com.enioka.jqm.jpamodel.JobDef.class).getResultList();

		em.clear();

		for (int i = 0; i < tmp.size(); i++)
		{
			jobs.add(new JobDefCustom(tmp.get(i), false));
		}
	}

	public String update()
	{
		EntityManagerFactory emf = Persistence.createEntityManagerFactory("jobqueue-api-pu");
		EntityManager em = emf.createEntityManager();

		System.out.println("ClassLoader Beans: " + JobDef.class.getClassLoader().hashCode());

		this.tmp = em.createQuery("SELECT j FROM JobDef j",
				com.enioka.jqm.jpamodel.JobDef.class).getResultList();

		em.clear();

		for (int i = 0; i < tmp.size(); i++)
		{
			jobs.add(new JobDefCustom(tmp.get(i), false));
		}

		return null;
	}


	public String enqueue()
	{
		for (JobDefCustom j : jobs) {
			if (j.isEnqueue())
			{
				JobDefinition jd = new JobDefinition(j.getJ().getApplicationName(), "jsf");
				Dispatcher.enQueue(jd);
			}
		}

		return "enqueued";
	}

	public JobDef getJ() {

		return j;
	}


	public void setJ(JobDef j) {

		willEnqueue.add(new JobDefCustom(j, true));
		this.j = j;

		EntityManagerFactory emf = Persistence.createEntityManagerFactory("jobqueue-api-pu");
		EntityManager em = emf.createEntityManager();

		System.out.println("ClassLoader Beans: " + JobDef.class.getClassLoader().hashCode());

		this.tmp = em.createQuery("SELECT j FROM JobDef j",
				com.enioka.jqm.jpamodel.JobDef.class).getResultList();

		em.clear();

		for (int i = 0; i < tmp.size(); i++)
		{
			jobs.add(new JobDefCustom(tmp.get(i), false));
		}
	}


	public List<JobDefCustom> getJobs() {

		EntityManagerFactory emf = Persistence.createEntityManagerFactory("jobqueue-api-pu");
		EntityManager em = emf.createEntityManager();

		System.out.println("ClassLoader Beans: " + JobDef.class.getClassLoader().hashCode());

		this.tmp = em.createQuery("SELECT j FROM JobDef j",
				com.enioka.jqm.jpamodel.JobDef.class).getResultList();

		em.clear();

		for (int i = 0; i < tmp.size(); i++)
		{
			jobs.add(new JobDefCustom(tmp.get(i), false));
		}

		return jobs;
	}


	public List<com.enioka.jqm.jpamodel.JobDef> getTmp() {

		return tmp;
	}


	public void setTmp(List<com.enioka.jqm.jpamodel.JobDef> tmp) {

		this.tmp = tmp;
	}


	public List<JobDefCustom> getWillEnqueue() {

		return willEnqueue;
	}


	public void setWillEnqueue(ArrayList<JobDefCustom> willEnqueue) {

		this.willEnqueue = willEnqueue;
	}


	public void setJobs(ArrayList<JobDefCustom> jobs) {

		this.jobs = jobs;

	}


	public int getCl() {

		return cl;
	}


	public void setCl(int cl) {

		this.cl = cl;
	}

}
