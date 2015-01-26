package org.ngsutils.mvpipe.runner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.mvpipe.exceptions.RunnerException;
import org.ngsutils.mvpipe.support.StringUtils;

public class SGERunner extends JobRunner {
	private static String[] defaultShellPaths = new String[] {"/bin/bash", "/usr/bin/bash", "/usr/local/bin/bash", "/bin/sh"};
	
	private static String findDefaultShell() {
		for (String path: defaultShellPaths) {
			if (new File(path).exists()) {
				return path;
			}
		}
		return null;
	}

	private boolean globalHold = false;
	private boolean hvmemIsTotal = true;
	private String account=null;
	private String parallelEnv = "shm";
	private String shell = findDefaultShell();
	
	private int dryRunJobCount = 0;

	private List<String> jobids = new ArrayList<String>();
	
	private JobDependency globalHoldJob = null;
	
	@Override
	public void done() throws RunnerException {
		super.done();
		log.info("submitted jobs: "+StringUtils.join(",", jobids));
		System.out.println(StringUtils.join("\n", jobids));
		
		if (globalHoldJob != null) {
			try {
				int retcode = Runtime.getRuntime().exec(new String[]{"qrls", globalHoldJob.getJobId()}).waitFor();
				if (retcode != 0) {
					throw new RunnerException("Unable to release global hold");
				}
			} catch (IOException | InterruptedException e) {
				throw new RunnerException(e);
			}
		}
	}

	@Override
	public void abort() {
		for (String jobid: jobids) {
			try {
				Runtime.getRuntime().exec(new String[]{"qdel", jobid}).waitFor();
			} catch (InterruptedException | IOException e) {
			}
		}
	}

	@Override
	public boolean submit(JobDefinition jobdef) throws RunnerException {
		if (jobdef.getSrc().equals("")) {
			return false;
		}
		
		if (globalHold) {
			if (globalHoldJob == null) {
				submitGlobalHold();
			}
			jobdef.addDependency(globalHoldJob);
		}
		
		String src = buildScript(jobdef);
		String jobid = submitScript(src);
		jobdef.setJobId(jobid);
		jobids.add(jobid);
			
		return true;
	}

	private void submitGlobalHold() throws RunnerException {
		String src = buildGlobalHoldScript();
		String jobid = submitScript(src);
		globalHoldJob = new ExistingJob(jobid);
		jobids.add(jobid);
	}

	private String buildGlobalHoldScript() {
        return 	"#!" + shell + "\n" +
        		"#$ -h\n" +
        		"#$ -terse\n" +
        		"#$ -N holding\n" +
        		"#$ -o /dev/null\n" +
        		"#$ -e /dev/null\n" +
        		"#$ -l h_rt=00:00:10\n" +
        		"sleep 1\n";
	}
	
	private String submitScript(String src) throws RunnerException {
		if (dryrun) {
			dryRunJobCount++;
			return "dryrun." + dryRunJobCount;
		}
		
		try {
			Process proc = Runtime.getRuntime().exec("qsub");
			OutputStream os = proc.getOutputStream();
			os.write(src.getBytes(Charset.forName("UTF8")));
			os.close();
			
			InputStream is = proc.getInputStream();
			InputStream err = proc.getErrorStream();
			int retcode = proc.waitFor();
			if (retcode != 0) {

				String errstr = "";
				int ch;
				while ((ch = err.read()) > -1) {
					errstr += (char) ch;
				}

				log.info(src);
				throw new RunnerException("Bad return code from qsub: "+retcode+" - "+errstr);
			}
			
			String jobid = "";
			int ch;
			while ((ch = is.read()) > -1) {
				jobid += (char) ch;
			}
			return StringUtils.strip(jobid);
		} catch (IOException | InterruptedException e) {
			throw new RunnerException(e);
		}
	}
	
	private String buildScript(JobDefinition jobdef) {
        String src = "#!" + shell + "\n";
        src += "#$ -w e\n";
        src += "#$ -terse\n";
        src += "#$ -N "+jobdef.getName()+"\n";

        if (jobdef.getSettingBool("job.hold", false)) {
            src += "#$ -h\n";
        }
        if (jobdef.getSettingBool("job.env", true)) {
            src += "#$ -V\n";
        }

        if (jobdef.hasSetting("job.walltime")) {
        	// this could be adjusted dynamically too...
        	src += "#$ -l h_rt="+jobdef.getSetting("job.walltime")+"\n";
        }

        if (jobdef.getSettingInt("job.procs", 1) > 1) {
            src += "#$ -pe "+parallelEnv+" "+jobdef.getSettingInt("job.procs")+"\n";
        }
        
        if (jobdef.hasSetting("job.stack")) {
            src += "#$ -l h_stack="+jobdef.getSetting("job.stack")+"\n";
        }

        if (jobdef.hasSetting("job.mem")) {
        	if (jobdef.getSettingInt("job.procs", 1) > 1 && !hvmemIsTotal) {
        		// convert hvmem to a per-slot amount
        		String mem = jobdef.getSetting("job.mem");
        		String units = "";
        		float memVal = 1;
        		while (mem.length() > 0) {
        			try {
        				memVal = Float.parseFloat(mem);
        				break;
        			} catch (NumberFormatException e) {
        				units = mem.substring(mem.length()-1) + units;
        				mem = mem.substring(0, mem.length()-1);
        			}
        		}
        		
                src += "#$ -l h_vmem="+(memVal / jobdef.getSettingInt("job.procs", 1)) + units+"\n";
        	} else {
                src += "#$ -l h_vmem="+jobdef.getSetting("job.mem")+"\n";
        	}
        }

        if (jobdef.getDependencies().size() > 0) {
        	List<String> depids = new ArrayList<String>();
        	for (JobDependency dep: jobdef.getDependencies()) {
        		depids.add(dep.getJobId());
        	}

            src += "#$ -hold_jid "+StringUtils.join(",", depids)+"\n";
        }
        
        if (jobdef.hasSetting("job.qos")) {
            // this is actually the "Project" in SGE terms
            src += "#$ -P "+jobdef.getSetting("job.qos")+"\n";
        }

        if (jobdef.hasSetting("job.queue")) {
            src += "#$ -q "+jobdef.getSetting("job.queue")+"\n";
        }

        if (jobdef.hasSetting("job.mail")) {
            src += "#$ -m "+jobdef.getSetting("job.mail")+"\n";
        }

        try {
			src += "#$ -wd "+jobdef.getSetting("job.wd", new File(".").getCanonicalPath())+"\n";
		} catch (IOException e) {
		}

        if (jobdef.hasSetting("job.account")) {
            src += "#$ -A "+jobdef.getSetting("job.account")+"\n";
        } else if (account != null) {
            src += "#$ -A "+account+"\n";
        }

        if (jobdef.hasSetting("job.stdout")) {
            src += "#$ -o "+jobdef.getSetting("job.stdout")+"\n";
        }

        if (jobdef.hasSetting("job.stderr")) {
            src += "#$ -e "+jobdef.getSetting("job.stderr")+"\n";
        }

        src += "#$ -notify\n";
        src += "FAILED=\"\"\n";
        src += "notify_stop() {\nkill_deps_signal \"SIGSTOP\"\n}\n";
        src += "notify_kill() {\nkill_deps_signal \"SIGKILL\"\n}\n";
        src += "kill_deps_signal() {\n";
        src += "  FAILED=\"1\"\n";
        src += "  kill_deps\n";
        src += "}\n";

        src += "kill_deps() {\n";
        src += "  DEPS=\"$(qstat -f -j $JOB_ID | grep jid_successor_list | awk \'{print $2}\' | sed -e \'s/,/ /g\')\"\n";
        src += "  if [ \"$DEPS\" != \"\" ]; then\n";
        src += "    qdel $DEPS\n";
        src += "  fi\n";
        src += "}\n";

        src += "trap notify_stop SIGUSR1\n";
        src += "trap notify_kill SIGUSR2\n";
    
        src += "set -o pipefail\nfunc () {\n";
        src += jobdef.getSrc();
        src += "\n  return $?\n}\n";
        	

        src += "func\n";
        src += "RETVAL=$?\n";
        src += "if [ \"$FAILED\" == \"\" ]; then\n";
        src += "  if [ $RETVAL -ne 0 ]; then\n";
        src += "    kill_deps\n";
        
        for (String out: jobdef.getOutputFilenames()) {
        	if (!jobdef.getSettingBool("keepfailed", false)) {
        		if (out.charAt(0) != '.') {
        			src += "    if [ -e \""+out+"\" ]; then rm \""+out+"\"; fi\n";
        		}
        	}
        }
        
        src += "  fi\n";

        src += "  exit $RETVAL\n";
        src += "else\n";
        src += "  # wait for SGE to kill the job for accounting purposes (max 60 sec)\n";
        src += "  I=0\n";
        src += "  while [ $I -lt 60 ]; do\n";
        src += "    sleep 1\n";
        src += "    let \"I=$I+1\"\n";
        src += "  done\n";
        src += "fi\n";
        
        return src;
	}

	/**
	 * SGE options:

		global_hold - start all pipelines with a "placeholder" job to synchronize
		              starts (and ensure that all jobs are submitted correctly
		              before releasing the pipeline
		
		account     - a default account to use
		
		parallelenv - the name of the parallel environment for multi-processor jobs
		              default: 'shm'
		
		hvmem_total - h_vmem should be specified as the total amount of memory, default
		              is to specify it as the amount of memory per-processor
		              (only used when procs > 1)
		
		shell       - a shell to use for the script (default(s): /bin/bash, /usr/bin/bash, /usr/local/bin/bash, /bin/sh)

	 */
	@Override
	protected void setConfig(String k, String val) {
		log.debug("Setting config: "+k+" => "+val);
		switch(k) {
		case "mvpipe.runner.sge.account":
			this.account = val;
			break;
		case "mvpipe.runner.sge.parallelenv":
			this.parallelEnv = val;
			break;
		case "mvpipe.runner.sge.global_hold":
			this.globalHold = val.toUpperCase().equals("TRUE");
			break;
		case "mvpipe.runner.sge.hvmem_total":
			this.hvmemIsTotal = val.toUpperCase().equals("TRUE");
			break;
		case "mvpipe.runner.sge.shell":
			this.shell = val;
			break;
		}
	}

}
