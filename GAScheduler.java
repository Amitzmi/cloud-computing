/**
 * Q8 - CloudSim implementation of:
 * "Joint optimization of resource utilization and makespan in cloud computing
 *  using genetic algorithm" (Dehury, Kumar, Kumar).
 *
 * Tasks (cloudlets) are mapped onto the 5 VMs given in the assignment using a GA whose
 * fitness jointly captures makespan and resource wastage. The best mapping is then
 * replayed inside CloudSim (bindCloudletToVm) so that the reported makespan is measured
 * by the simulator and cross-checked against the GA's analytic model.
 */



import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;
import java.io.*;
import java.util.*;

public class GAScheduler {

    // ---------------- VM configuration (from the assignment table) ----------------
    static final int V = 5;
    static final int[]  CORES = {4, 2, 8, 6, 2};
    static final int[]  RAM   = {8192, 4096, 16384, 8192, 8192};   // MB
    static final int[]  BW    = {1000, 500, 2000, 1000, 500};      // Mbps
    static final double MIPS  = 1000;                              // per core (assumption)
    static final int TOTAL_CORES = 22;
    static final double EPS = 1e-4;

    // ---------------- GA parameters ----------------
    static int POP = 100, GENS = 300, ELITE = 2, TOURN = 3;
    static double PC = 0.8, PM = 0.02, ALPHA = 0.5;
    static int RUNS = 30;

    // ---------------- Workload ----------------
    static class Tasks {
        int n; double[] len; double[] mem; double lb;
        Tasks(int n, long seed) {
            this.n = n; len = new double[n]; mem = new double[n];
            Random r = new Random(seed); double sum = 0;
            for (int i = 0; i < n; i++) {
                len[i] = 5000 + r.nextInt(45001);      // 5,000 - 50,000 MI
                mem[i] = 128 + r.nextInt(897);         // 128 - 1024 MB
                sum += len[i] / MIPS;
            }
            lb = sum / TOTAL_CORES;                    
        }
    }

    static class Metrics {
        double makespan, ucpu, umem, wastage, di, fitness;
    }

    // Analytic model that mirrors CloudSim's space-shared VM scheduler:
    // tasks are queued per VM in id order and start on the earliest free core.
    static Metrics evaluate(int[] map, Tasks t, double alpha) {
        double[][] free = new double[V][];
        for (int j = 0; j < V; j++) free[j] = new double[CORES[j]];
        double[] busy = new double[V], memT = new double[V];
        for (int i = 0; i < t.n; i++) {
            int j = map[i]; double[] f = free[j];
            int k = 0;
            for (int c = 1; c < f.length; c++) if (f[c] < f[k]) k = c;
            double d = t.len[i] / MIPS;
            f[k] += d; busy[j] += d; memT[j] += t.mem[i] * d;
        }
        double[] fin = new double[V]; double M = 0, sumBusy = 0;
        double fmin = Double.MAX_VALUE, fsum = 0;
        for (int j = 0; j < V; j++) {
            for (double x : free[j]) fin[j] = Math.max(fin[j], x);
            M = Math.max(M, fin[j]); sumBusy += busy[j];
            fmin = Math.min(fmin, fin[j]); fsum += fin[j];
        }
        Metrics m = new Metrics(); m.makespan = M;
        m.ucpu = sumBusy / (TOTAL_CORES * M);
        double sumMem = 0, ramTot = 0, w = 0;
        for (int j = 0; j < V; j++) {
            double uc = busy[j] / (CORES[j] * M), um = memT[j] / (RAM[j] * M);
            sumMem += memT[j]; ramTot += RAM[j];
            double wj = (uc + um) == 0 ? 1.0 : Math.min(1.0, (Math.abs(um - uc) + EPS) / (uc + um));
            w += wj;
        }
        m.umem = sumMem / (ramTot * M);
        m.wastage = w / V;
        m.di = (M - fmin) / (fsum / V);
        m.fitness = alpha * (M / t.lb) + (1 - alpha) * m.wastage;
        return m;
    }

    // ---------------- Baseline schedulers ----------------
    static int[] randomMap(Tasks t, Random r) {
        int[] m = new int[t.n]; for (int i = 0; i < t.n; i++) m[i] = r.nextInt(V); return m;
    }
    static int[] roundRobin(Tasks t) {
        int[] m = new int[t.n]; for (int i = 0; i < t.n; i++) m[i] = i % V; return m;
    }
    static double earliest(double[] f) { double b = f[0]; for (double x : f) b = Math.min(b, x); return b; }
    static void place(double[] f, double d) {
        int k = 0; for (int c = 1; c < f.length; c++) if (f[c] < f[k]) k = c; f[k] += d;
    }
    static double[][] newState() {
        double[][] s = new double[V][]; for (int j = 0; j < V; j++) s[j] = new double[CORES[j]]; return s;
    }
    /** Greedy / Minimum Completion Time in arrival order. */
    static int[] mct(Tasks t) {
        double[][] s = newState(); int[] m = new int[t.n];
        for (int i = 0; i < t.n; i++) {
            double d = t.len[i] / MIPS; int bj = 0; double bc = Double.MAX_VALUE;
            for (int j = 0; j < V; j++) { double c = earliest(s[j]) + d; if (c < bc) { bc = c; bj = j; } }
            m[i] = bj; place(s[bj], d);
        }
        return m;
    }
    /** Min-Min (pickMax=false) and Max-Min (pickMax=true). */
    static int[] minMax(Tasks t, boolean pickMax) {
        double[][] s = newState(); int[] m = new int[t.n]; boolean[] done = new boolean[t.n];
        for (int step = 0; step < t.n; step++) {
            int bt = -1, bj = -1; double bv = pickMax ? -1 : Double.MAX_VALUE;
            for (int i = 0; i < t.n; i++) {
                if (done[i]) continue;
                double d = t.len[i] / MIPS; int mj = 0; double mc = Double.MAX_VALUE;
                for (int j = 0; j < V; j++) { double c = earliest(s[j]) + d; if (c < mc) { mc = c; mj = j; } }
                if (pickMax ? mc > bv : mc < bv) { bv = mc; bt = i; bj = mj; }
            }
            done[bt] = true; m[bt] = bj; place(s[bj], t.len[bt] / MIPS);
        }
        return m;
    }

    // ---------------- Genetic Algorithm ----------------
    static int[] ga(Tasks t, Random r, double alpha, double[] trace) {
        int n = t.n;
        int[][] pop = new int[POP][n]; double[] fit = new double[POP];
        for (int p = 0; p < POP; p++) {
            for (int i = 0; i < n; i++) pop[p][i] = r.nextInt(V);
            fit[p] = evaluate(pop[p], t, alpha).fitness;
        }
        for (int g = 0; g < GENS; g++) {
            Integer[] idx = new Integer[POP];
            for (int i = 0; i < POP; i++) idx[i] = i;
            final double[] ff = fit;
            Arrays.sort(idx, (a, b) -> Double.compare(ff[a], ff[b]));
            if (trace != null) trace[g] = fit[idx[0]];
            int[][] next = new int[POP][]; double[] nf = new double[POP];
            for (int e = 0; e < ELITE; e++) { next[e] = pop[idx[e]].clone(); nf[e] = fit[idx[e]]; }
            for (int p = ELITE; p < POP; p++) {
                int[] a = pop[tournament(fit, r)], b = pop[tournament(fit, r)];
                int[] c = a.clone();
                if (r.nextDouble() < PC) {                    // single-point crossover
                    int cut = 1 + r.nextInt(n - 1);
                    for (int i = cut; i < n; i++) c[i] = b[i];
                }
                for (int i = 0; i < n; i++) if (r.nextDouble() < PM) c[i] = r.nextInt(V);   // mutation
                next[p] = c; nf[p] = evaluate(c, t, alpha).fitness;
            }
            pop = next; fit = nf;
        }
        int best = 0; for (int p = 1; p < POP; p++) if (fit[p] < fit[best]) best = p;
        if (trace != null) trace[GENS - 1] = Math.min(trace[GENS - 1], fit[best]);
        return pop[best];
    }
    static int tournament(double[] fit, Random r) {
        int b = r.nextInt(POP);
        for (int k = 1; k < TOURN; k++) { int c = r.nextInt(POP); if (fit[c] < fit[b]) b = c; }
        return b;
    }

    // ---------------- CloudSim replay ----------------
    /** Executes a task->VM mapping in CloudSim 4.0 and returns the measured makespan. */
    static double cloudSim(int[] map, Tasks t) throws Exception {
        Log.disable();
        CloudSim.init(1, Calendar.getInstance(), false);
        List<Host> hosts = new ArrayList<>();
        for (int h = 0; h < 2; h++) {                     // 2 hosts: 16 cores, 32 GB, 10 Gbps
            List<Pe> pes = new ArrayList<>();
            for (int c = 0; c < 16; c++) pes.add(new Pe(c, new PeProvisionerSimple(MIPS)));
            hosts.add(new Host(h, new RamProvisionerSimple(32768), new BwProvisionerSimple(10000),
                    1000000, pes, new VmSchedulerTimeShared(pes)));
        }
        DatacenterCharacteristics dc = new DatacenterCharacteristics("x86", "Linux", "Xen", hosts,
                5.0, 3.0, 0.05, 0.001, 0.0);
        new Datacenter("DC0", dc, new VmAllocationPolicySimple(hosts), new LinkedList<Storage>(), 0);
        DatacenterBroker br = new DatacenterBroker("Broker");
        List<Vm> vms = new ArrayList<>();
        for (int j = 0; j < V; j++)
            vms.add(new Vm(j, br.getId(), MIPS, CORES[j], RAM[j], BW[j], 10000, "Xen",
                    new CloudletSchedulerSpaceShared()));
        br.submitVmList(vms);
        List<Cloudlet> cls = new ArrayList<>();
        UtilizationModel full = new UtilizationModelFull();
        for (int i = 0; i < t.n; i++) {
            Cloudlet c = new Cloudlet(i, (long) t.len[i], 1, 300, 300, full, full, full);
            c.setUserId(br.getId()); cls.add(c);
        }
        br.submitCloudletList(cls);
        for (int i = 0; i < t.n; i++) br.bindCloudletToVm(i, map[i]);
        CloudSim.startSimulation();
        List<Cloudlet> res = br.getCloudletReceivedList();
        CloudSim.stopSimulation();
        double start = Double.MAX_VALUE, end = 0;
        for (Cloudlet c : res) { start = Math.min(start, c.getExecStartTime()); end = Math.max(end, c.getFinishTime()); }
        if (res.size() != t.n) throw new RuntimeException("Only " + res.size() + " cloudlets finished");
        return end - start;
    }

    // ---------------- Experiments ----------------
    static String row(String... a) { return String.join(",", a); }
    static String f(double x) { return String.format(Locale.US, "%.4f", x); }

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "results";
        new java.io.File(out).mkdirs();
        int[] sizes = {50, 100, 200, 400, 800};
        int MAIN = 100;

        PrintWriter main = new PrintWriter(out + "/main_runs.csv");
        main.println("algorithm,run,makespan,cpu_util,mem_util,wastage,imbalance,fitness,time_ms");
        PrintWriter scale = new PrintWriter(out + "/scalability.csv");
        scale.println("tasks,algorithm,makespan_mean,makespan_std,cpu_util_mean,wastage_mean,imbalance_mean,time_ms_mean");
        PrintWriter val = new PrintWriter(out + "/cloudsim_validation.csv");
        val.println("tasks,algorithm,analytic_makespan,cloudsim_makespan,abs_diff,pct_diff");
        PrintWriter conv = new PrintWriter(out + "/convergence.csv");
        conv.println("generation,mean_best_fitness,min_best_fitness,max_best_fitness");
        PrintWriter alp = new PrintWriter(out + "/alpha_tradeoff.csv");
        alp.println("alpha,makespan_mean,makespan_std,cpu_util_mean,wastage_mean,imbalance_mean");
        PrintWriter vmload = new PrintWriter(out + "/vm_load_main.csv");
        vmload.println("algorithm,vm,tasks_assigned");

        String[] algos = {"Random", "Round Robin", "Greedy-MCT", "Min-Min", "Max-Min", "GA (proposed)"};
        for (int n : sizes) {
            Tasks t = new Tasks(n, 42 + n);
            Map<String, List<Metrics>> all = new LinkedHashMap<>();
            Map<String, Double> timeMs = new HashMap<>();
            Map<String, int[]> repMap = new HashMap<>();
            for (String a : algos) all.put(a, new ArrayList<>());
            double[][] traces = new double[RUNS][GENS];
            for (int run = 0; run < RUNS; run++) {
                Random r = new Random(1000L * n + run);
                for (String a : algos) {
                    long t0 = System.nanoTime(); int[] map;
                    switch (a) {
                        case "Random": map = randomMap(t, r); break;
                        case "Round Robin": map = roundRobin(t); break;
                        case "Greedy-MCT": map = mct(t); break;
                        case "Min-Min": map = minMax(t, false); break;
                        case "Max-Min": map = minMax(t, true); break;
                        default: map = ga(t, r, ALPHA, traces[run]);
                    }
                    double ms = (System.nanoTime() - t0) / 1e6;
                    Metrics m = evaluate(map, t, ALPHA);
                    all.get(a).add(m);
                    timeMs.merge(a, ms, Double::sum);
                    if (run == 0) repMap.put(a, map);
                    if (n == MAIN)
                        main.println(row(a, "" + run, f(m.makespan), f(m.ucpu), f(m.umem), f(m.wastage), f(m.di), f(m.fitness), f(ms)));
                }
            }
            for (String a : algos) {
                List<Metrics> L = all.get(a);
                double[] mk = new double[RUNS];
                double cu = 0, wa = 0, di = 0;
                for (int i = 0; i < RUNS; i++) { mk[i] = L.get(i).makespan; cu += L.get(i).ucpu; wa += L.get(i).wastage; di += L.get(i).di; }
                double mean = Arrays.stream(mk).average().getAsDouble(), var = 0;
                for (double x : mk) var += (x - mean) * (x - mean);
                scale.println(row("" + n, a, f(mean), f(Math.sqrt(var / RUNS)), f(cu / RUNS), f(wa / RUNS), f(di / RUNS), f(timeMs.get(a) / RUNS)));
                // CloudSim validation using run-0 mapping
                int[] map = repMap.get(a);
                double an = evaluate(map, t, ALPHA).makespan, cs = cloudSim(map, t);
                val.println(row("" + n, a, f(an), f(cs), f(Math.abs(an - cs)), f(100 * Math.abs(an - cs) / an)));
                if (n == MAIN) {
                    int[] cnt = new int[V]; for (int x : map) cnt[x]++;
                    for (int j = 0; j < V; j++) vmload.println(row(a, "VM" + (j + 1), "" + cnt[j]));
                }
            }
            if (n == MAIN) {
                for (int g = 0; g < GENS; g++) {
                    double s = 0, mn = Double.MAX_VALUE, mx = 0;
                    for (int r = 0; r < RUNS; r++) { s += traces[r][g]; mn = Math.min(mn, traces[r][g]); mx = Math.max(mx, traces[r][g]); }
                    conv.println(row("" + g, f(s / RUNS), f(mn), f(mx)));
                }
                // alpha trade-off study (0 = wastage only ... 1 = makespan only)
                for (double al : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
                    double[] mk = new double[RUNS]; double cu = 0, wa = 0, di = 0;
                    for (int run = 0; run < RUNS; run++) {
                        Metrics m = evaluate(ga(t, new Random(777L + run), al, null), t, al);
                        mk[run] = m.makespan; cu += m.ucpu; wa += m.wastage; di += m.di;
                    }
                    double mean = Arrays.stream(mk).average().getAsDouble(), var = 0;
                    for (double x : mk) var += (x - mean) * (x - mean);
                    alp.println(row(f(al), f(mean), f(Math.sqrt(var / RUNS)), f(cu / RUNS), f(wa / RUNS), f(di / RUNS)));
                }
                System.out.printf("Main scenario n=%d  lower bound=%.3f s%n", n, t.lb);
            }
            System.out.println("done n=" + n);
        }
        main.close(); scale.close(); val.close(); conv.close(); alp.close(); vmload.close();
    }
}
