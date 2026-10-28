import pandas as pd, numpy as np, matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
plt.rcParams.update({'font.size':10,'axes.spines.top':False,'axes.spines.right':False,'figure.dpi':150})
order=['Random','Round Robin','Min-Min','Max-Min','Greedy-MCT','GA (proposed)']
cols=['#b0b7c3','#8f9bb0','#6f86ad','#4f74a8','#2f62a1','#d9541e']
R='results/'
m=pd.read_csv(R+'main_runs.csv'); g=m.groupby('algorithm').agg(['mean','std'])

# 1 makespan
fig,ax=plt.subplots(figsize=(6.5,3.6))
mu=[g.loc[a,('makespan','mean')] for a in order]; sd=[g.loc[a,('makespan','std')] for a in order]
b=ax.bar(order,mu,yerr=sd,color=cols,capsize=3)
ax.axhline(130.652,ls='--',c='k',lw=1); ax.text(-0.4,134,'Lower bound = 130.65 s',fontsize=8)
for r,v in zip(b,mu): ax.text(r.get_x()+r.get_width()/2,v+ (18 if v>200 else 3)+ (sd[list(b).index(r)]),f'{v:.1f}',ha='center',fontsize=8)
ax.set_ylabel('Makespan (s)'); ax.set_title('Makespan comparison (100 tasks, mean ± SD over 30 runs)')
plt.xticks(rotation=20); plt.tight_layout(); plt.savefig('figs/fig_makespan.png'); plt.close()

# 2 utilization & wastage
fig,axs=plt.subplots(1,2,figsize=(7,3.4))
cu=[g.loc[a,('cpu_util','mean')]*100 for a in order]; mu_=[g.loc[a,('mem_util','mean')]*100 for a in order]
x=np.arange(len(order)); w=0.38
axs[0].bar(x-w/2,cu,w,label='CPU',color='#2f62a1'); axs[0].bar(x+w/2,mu_,w,label='Memory',color='#e0a030')
axs[0].set_xticks(x); axs[0].set_xticklabels(order,rotation=35,ha='right',fontsize=8); axs[0].set_ylabel('Utilization (%)'); axs[0].legend(fontsize=8); axs[0].set_title('Resource utilization')
di=[g.loc[a,('imbalance','mean')] for a in order]
axs[1].bar(order,di,color=cols); axs[1].set_ylabel('Degree of imbalance'); axs[1].set_title('Load imbalance (lower is better)')
axs[1].set_xticks(range(len(order))); axs[1].set_xticklabels(order,rotation=35,ha='right',fontsize=8)
plt.tight_layout(); plt.savefig('figs/fig_util.png'); plt.close()

# 3 convergence
c=pd.read_csv(R+'convergence.csv')
fig,ax=plt.subplots(figsize=(6.5,3.4))
ax.fill_between(c.generation,c.min_best_fitness,c.max_best_fitness,alpha=.2,color='#d9541e',label='min–max over 30 runs')
ax.plot(c.generation,c.mean_best_fitness,color='#d9541e',label='mean best fitness')
ax.set_xlabel('Generation'); ax.set_ylabel('Fitness (lower is better)'); ax.set_title('GA convergence (100 tasks)'); ax.legend(fontsize=8)
plt.tight_layout(); plt.savefig('figs/fig_convergence.png'); plt.close()

# 4 scalability
s=pd.read_csv(R+'scalability.csv')
fig,axs=plt.subplots(1,2,figsize=(7.4,3.4))
for a,col in zip(order,cols):
    d=s[s.algorithm==a]; axs[0].plot(d.tasks,d.makespan_mean,marker='o',ms=3,label=a,color=col,lw=2 if a.startswith('GA') else 1.2)
    axs[1].plot(d.tasks,d.cpu_util_mean*100,marker='o',ms=3,color=col,lw=2 if a.startswith('GA') else 1.2)
axs[0].set_yscale('log'); axs[0].set_xscale('log'); axs[0].set_xticks([50,100,200,400,800]); axs[0].set_xticklabels([50,100,200,400,800])
axs[0].set_xlabel('Number of tasks'); axs[0].set_ylabel('Makespan (s, log)'); axs[0].set_title('Makespan vs workload size'); axs[0].legend(fontsize=7)
axs[1].set_xscale('log'); axs[1].set_xticks([50,100,200,400,800]); axs[1].set_xticklabels([50,100,200,400,800])
axs[1].set_xlabel('Number of tasks'); axs[1].set_ylabel('CPU utilization (%)'); axs[1].set_title('CPU utilization vs workload size')
plt.tight_layout(); plt.savefig('figs/fig_scale.png'); plt.close()

# 5 alpha
a=pd.read_csv(R+'alpha_tradeoff.csv'); a2=a[a.alpha>0]
fig,ax=plt.subplots(figsize=(6.2,3.3)); ax.errorbar(a2.alpha,a2.makespan_mean,yerr=a2.makespan_std,marker='o',color='#2f62a1',capsize=3)
ax.set_xlabel('Weight α on makespan (1−α on wastage)'); ax.set_ylabel('Makespan (s)',color='#2f62a1')
ax2=ax.twinx(); ax2.plot(a2.alpha,a2.wastage_mean,marker='s',color='#d9541e'); ax2.set_ylabel('Mean resource wastage',color='#d9541e'); ax2.spines['right'].set_visible(True)
ax.set_title('Effect of objective weight α (α = 0 omitted: degenerate)'); plt.tight_layout(); plt.savefig('figs/fig_alpha.png'); plt.close()

# 6 VM load
v=pd.read_csv(R+'vm_load_main.csv'); fig,ax=plt.subplots(figsize=(6.5,3.4))
pv=v.pivot(index='vm',columns='algorithm',values='tasks_assigned')[order]; pv.plot.bar(ax=ax,color=cols,width=.8,rot=0)
ax.set_ylabel('Tasks assigned'); ax.set_title('Task distribution over VMs (100 tasks; VM cores 4,2,8,6,2)'); ax.legend(fontsize=7,ncol=2)
plt.tight_layout(); plt.savefig('figs/fig_vmload.png'); plt.close()

# 7 architecture
fig,ax=plt.subplots(figsize=(7.2,3.9)); ax.axis('off'); ax.set_xlim(0,10); ax.set_ylim(0,6)
def box(x,y,w,h,t,c='#e8eef8',fs=8.5):
    ax.add_patch(FancyBboxPatch((x,y),w,h,boxstyle='round,pad=0.05,rounding_size=0.1',fc=c,ec='#2f4a75',lw=1)); ax.text(x+w/2,y+h/2,t,ha='center',va='center',fontsize=fs)
def arr(x1,y1,x2,y2): ax.add_patch(FancyArrowPatch((x1,y1),(x2,y2),arrowstyle='->',mutation_scale=12,color='#333'))
box(0.2,4.3,2.4,1.3,'Workload generator\n(100–800 cloudlets:\nlength, memory)')
box(3.7,4.3,2.6,1.3,'Genetic-algorithm\nscheduler\n(chromosome = task→VM)','#fbe3d6')
box(7.3,4.3,2.5,1.3,'Fitness: α·M/LB\n+ (1−α)·wastage')
arr(2.6,4.95,3.7,4.95); arr(6.3,4.95,7.3,4.95); arr(7.3,4.5,6.3,4.5)
box(3.7,2.7,2.6,1.0,'Best mapping\n(cloudlet → VM binding)')
arr(5,4.3,5,3.7)
box(0.2,0.2,9.6,2.0,'',c='#f4f4f4'); ax.text(0.4,1.95,'CloudSim 4.0 environment',fontsize=8.5,weight='bold')
box(0.5,0.4,1.9,1.2,'DatacenterBroker'); box(2.9,0.4,2.4,1.2,'Datacenter DC0\n2 hosts × 16 cores\n32 GB, 10 Gbps'); 
for i,(n,cx) in enumerate([('VM1\n4c/8G',5.7),('VM2\n2c/4G',6.65),('VM3\n8c/16G',7.6),('VM4\n6c/8G',8.55),('VM5\n2c/8G',9.5)]): pass
xs=[5.65,6.55,7.45,8.35,9.25]
for n,x in zip(['VM1\n4c·8G','VM2\n2c·4G','VM3\n8c·16G','VM4\n6c·8G','VM5\n2c·8G'],xs): box(x-0.42,0.45,0.84,1.1,n,'#dff0e0',7)
arr(2.4,1.0,2.9,1.0); arr(5,2.7,1.5,1.6)
ax.text(0.2,3.05,'Metrics: makespan,\nCPU/memory utilization,\nwastage, imbalance',fontsize=8)
plt.savefig('figs/fig_arch.png',bbox_inches='tight'); plt.close()

# 8 flowchart
fig,ax=plt.subplots(figsize=(3.2,5.6)); ax.axis('off'); ax.set_xlim(0,4); ax.set_ylim(0,11)
steps=['Generate tasks & VM set','Initialise population\n(100 random mappings)','Evaluate fitness\n(analytic space-shared model)','Selection (tournament k=3)\n+ elitism (2)','Single-point crossover\n(p = 0.8)','Mutation (p = 0.02 / gene)','Max generations (300)?','Best mapping → CloudSim\nbindCloudletToVm()','Measure makespan,\nutilization, wastage']
ys=np.linspace(10.2,0.4,len(steps))
for s_,y in zip(steps,ys): box(0.3,y-0.35,3.4,0.75,s_,'#fbe3d6' if 'Max' in s_ else '#e8eef8',8)
for i in range(len(ys)-1): arr(2,ys[i]-0.36,2,ys[i+1]+0.4)
ax.annotate('',xy=(0.15,ys[2]),xytext=(0.15,ys[6]),arrowprops=dict(arrowstyle='->',connectionstyle='arc3,rad=0'))
ax.text(0.02,(ys[2]+ys[6])/2,'no',rotation=90,fontsize=7,va='center')
plt.savefig('figs/fig_flow.png',bbox_inches='tight'); plt.close()
