package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ast.DCLDeepDependencyVisitor;
import dependencies.Dependency;
import enums.DependencyType;
import exception.DCLException;
import util.DCLUtil;

public class Architecture {
	private final Logger LOG;
	/**
	 * String: class name Collection<Dependency>: Collection of established
	 * dependencies
	 */
	public Map<String, Collection<Dependency>> projectClasses = new ConcurrentHashMap<String, Collection<Dependency>>();

	/**
	 * String: module name String: module description
	 */
	public Map<String, String> modules = new ConcurrentHashMap<String, String>();

	/**
	 * Collection<DependencyConstraint>: Collection of dependency constraints
	 */

	/**
	 * List of all ITypeBinding
	 * 
	 */
	public List<ITypeBinding> typeBindings = new CopyOnWriteArrayList<ITypeBinding>();


	public Architecture(String projectPath) throws CoreException, IOException, DCLException, InterruptedException {

		LOG = LoggerFactory.getLogger(Architecture.class);
		List<String> classPath = new LinkedList<String>();
		List<String> sourcePath = new LinkedList<String>();

		classPath.addAll(DCLUtil.getPath(projectPath));
		sourcePath.addAll(DCLUtil.getSource(projectPath));

		String[] classPathEntries = classPath.toArray(new String[classPath.size()]);
		String[] sourcePathEntries = sourcePath.toArray(new String[sourcePath.size()]);

		Collection<String> filesFromProject = DCLUtil.getFilesFromProject(projectPath);
		extractDependencies(classPathEntries, sourcePathEntries, filesFromProject);

	}

	public Architecture(Logger log) {
		this.LOG = log;
	}

	public void extractDependencies(String[] classPathEntries, String[] sourcePathEntries,
			Collection<String> filesFromProject) {
		filesFromProject.parallelStream().forEach(f -> {
			try {
				DCLDeepDependencyVisitor ddv = DCLUtil.useAST(f, classPathEntries, sourcePathEntries);
				Collection<Dependency> deps = ddv.getDependencies();
				// this.filterCommonDependencies(deps);
				this.projectClasses.put(ddv.getClassName(), deps);
				this.typeBindings.add(ddv.getITypeBinding());
			} catch (Throwable e) {
				LOG.error("Cannot handle file " + f + " due to error:  " + e.getMessage(), e);
			}
		});
	}

	public Set<String> getProjectClasses() {
		return projectClasses.keySet();
	}

	public Collection<Dependency> getDependencies(String className) {
		return projectClasses.get(className);
	}

	/**
	 * provide dependencies between class A and B
	 * @return
	 */
	public Collection<Dependency> getClassDependencies() {
		Collection<Dependency> listDep = new ArrayList<Dependency>();
		for (Map.Entry<String, Collection<Dependency>> entryDep : projectClasses.entrySet()) {
			for (Dependency dep : entryDep.getValue()) {
				listDep.add(dep);
			}
		}
		return listDep;
	}
	/**
	 * provide dependencies as list of CSV lines
	 * @return
	 */
	public Collection<String> getDependencies() {
		return getClassDependencies().stream().map(Architecture::toCSV).collect(Collectors.toSet());
	}

	public static String toCSV(Dependency dep) {
		String s = dep.getClassNameA() + "," + dep.getDependencyType().getValue() + "," + dep.getClassNameB() +","+dep.getLineNumber();
		return s;
	}

	public Dependency getDependency(String classNameA, String classNameB, Integer lineNumberA,
			DependencyType dependencyType) {
		Collection<Dependency> dependencies = projectClasses.get(classNameA);
		for (Dependency d : dependencies) {
			if (lineNumberA == null) {

			}
			if ((lineNumberA == null) ? d.getLineNumber() == null
					: lineNumberA.equals(d.getLineNumber()) && d.getClassNameB().equals(classNameB)
							&& d.getDependencyType().equals(dependencyType)) {
				return d;
			}
		}
		return null;
	}

	public void updateDependencies(String className, Collection<Dependency> dependencies) {
		projectClasses.put(className, dependencies);
	}

	private void filterCommonDependencies(Collection<Dependency> dependencies) {
		try {
			String typesToDisregard[] = new String[] { "boolean", "char", "byte", "short", "int", "long", "float",
					"double",

					"java.lang.Boolean", "java.util.Vector", "java.util.Iterator", "java.lang.Class",
					"java.lang.Character", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
					"java.lang.Float", "java.lang.Double", "java.lang.String", "java.lang.Object",
					"java.lang.Boolean[]", "java.lang.Boolean[][]", "java.lang.Character[]", "java.lang.Character[][]",
					"java.lang.Byte[]", "java.lang.Byte[][]", "java.lang.Short[]", "java.lang.Short[][]",
					"java.lang.Integer[]", "java.lang.Integer[][]", "java.lang.Long[]", "java.lang.Long[][]",
					"java.lang.Float[]", "java.lang.Float[][]", "java.lang.Double[]", "java.lang.Double[][]",
					"java.lang.String[]", "java.lang.String[][]", "java.lang.Object[]", "java.lang.Object[][]",
					"java.lang.Deprecated", "java.util.ArrayList", "java.util.ArrayList[]", "java.util.ArrayList[][]",
					"java.util.ArrayList<^[a-zA-Z]>", "java.util.ArrayList<^[a-zA-Z]>[]",
					"java.util.ArrayList<^.*>[][]", "java.util.ArrayList[]<.*>", "java.util.ArrayList[][]<.*>",
					"java.lang.SuppressWarnings", "java.lang.Override", "java.lang.SafeVarargs",
					"java.util.ArrayList<SQLData>", "java.util.ArrayList<String>", "java.util.ArrayList<JPanel>",
					"java.util.ArrayList<Long>", "java.util.ArrayList<Double>", "java.util.ArrayList<Integer>" };

			for (Iterator<Dependency> it = dependencies.iterator(); it.hasNext();) {
				Dependency d = it.next();
				if (contains(d.getClassNameA(), typesToDisregard) || contains(d.getClassNameB(), typesToDisregard)) {
					it.remove();
				}
			}
		} catch (NullPointerException npe) {
			LOG.error("Error in Filtering Common Dependencies: dependendencies=" + dependencies);
		}

	}

	private boolean contains(Object value, Object[] array) {
		for (Object o : array) {
			if (o.equals(value) || value.toString().startsWith("java.util.ArrayList")) {
				return true;
			}
		}
		return false;
	}

	public List<ITypeBinding> getITypeBindings() {
		return typeBindings;
	}

}
