package net.runelite.deob.execution;

import net.runelite.deob.ClassFile;
import net.runelite.deob.ClassGroup;
import net.runelite.deob.Deob;
import net.runelite.deob.Method;
import net.runelite.deob.attributes.code.Instruction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.deob.attributes.code.instruction.types.FieldInstruction;
import net.runelite.deob.attributes.code.instruction.types.GetFieldInstruction;
import net.runelite.deob.attributes.code.instruction.types.InvokeInstruction;
import net.runelite.deob.attributes.code.instructions.InvokeStatic;
import net.runelite.deob.deobfuscators.arithmetic.Encryption;
import net.runelite.deob.deobfuscators.rename.graph.EdgeType;
import net.runelite.deob.deobfuscators.rename.graph.Graph;
import org.apache.commons.collections4.map.MultiValueMap;

public class Execution
{
	private ClassGroup group;
	public List<Frame> frames = new LinkedList<>(),
			processedFrames = new LinkedList<>();
	public Set<Method> methods = new HashSet<>(); // all methods
	public Set<Instruction> executed = new HashSet<>(); // executed instructions
	private MultiValueMap<InstructionContext, Method> invokes = new MultiValueMap<>();
	private Encryption encryption;
	public MultiValueMap<Instruction, InstructionContext> contexts = new MultiValueMap<>();
	private Map<Method, MethodContext> methodContexts = new HashMap<>();
	private boolean buildGraph; // if true the frame graph is built and execution hasJumped also compares previous instructions
	private Graph graph = new Graph();

	public Execution(ClassGroup group)
	{
		this.group = group;
	}

	public Encryption getEncryption()
	{
		return encryption;
	}
	
	public void setEncryption(Encryption encryption)
	{
		this.encryption = encryption;
	}
	
	public List<Method> getInitialMethods()
	{
		List<Method> methods = new ArrayList<>();
		
		group.buildClassGraph(); // required when looking up methods
		group.lookup(); // lookup methods
		
		for (ClassFile cf : group.getClasses())
		{
			for (Method m : cf.getMethods().getMethods())
			{
				if (!Deob.isObfuscated(m.getName()) && !m.getName().equals("<init>"))
				{
					if (m.getCode() == null)
					{
						methods.add(m);
						continue;
					}
					
					methods.add(m); // I guess this method name is overriding a jre interface (init, run, ?).
				}
			}
		}
		
		return methods;
	}
	
	public void populateInitialMethods()
	{
		for (Method m : this.getInitialMethods())
		{
			if (m.getCode() == null)
			{
				methods.add(m);
				continue;
			}

			Frame frame = new Frame(this, m);
			frame.initialize();
			addFrame(frame); // I guess this method name is overriding a jre interface (init, run, ?).
		}
	}
	
	private boolean hasInvoked(InstructionContext from, Method to)
	{
		Collection<Method> methods = invokes.getCollection(from);
		if (methods != null && methods.contains(to))
			return true;
		
		invokes.put(from, to);
		return false;
	}

	private void addFrame(Frame frame)
	{
		frames.add(frame);
		//frames.add(0, frame);
	}
	
	public void invoke(InstructionContext from, Method to)
	{
		Frame frame = from.getFrame();
		
//		if (!this.isFollowInvokes() && !to.isStatic())
//			return;
		
		if (hasInvoked(from, to))
			return;
		
		Frame f = new Frame(this, to);
		f.initialize(from);
		this.addFrame(f);
		
	//	if (!this.followInvokes && to.isStatic())
	//		frame.stop(); // frames continue from the method
	}
	
	public void addMethod(Method to)
	{
		Frame f = new Frame(this, to);
		f.initialize();
		this.addFrame(f);
	}
	
	public void run()
	{
		int fcount = 0;
		while (!frames.isEmpty())
		{
			Frame frame = frames.get(0);
			
			methods.add(frame.getMethod());
			
			if (!frame.isExecuting())
			{
				frames.remove(0);
				processedFrames.add(frame);
				continue;
			}
			
			++fcount;
			assert frame.isExecuting();
			frame.execute();
			
//			if (!frame.isExecuting())
//			{
				assert frames.get(0) == frame;
				frames.remove(frame);
				processedFrames.add(frame);
//			}
//			else
//			{
//				// another frame takes priority
//			}
		}
		
		System.out.println("Processed " + fcount + " frames");
	}
	
	public Collection<InstructionContext> getInstructonContexts(Instruction i)
	{
		return contexts.getCollection(i);
	}
	
	public MethodContext getMethodContext(Method m)
	{
		MethodContext c = methodContexts.get(m);
		if (c != null)
			return c;
		
		c = new MethodContext(this);
		methodContexts.put(m, c);
		return c;
	}

	public boolean isBuildGraph()
	{
		return buildGraph;
	}

	public void setBuildGraph(boolean buildGraph)
	{
		this.buildGraph = buildGraph;
	}
	
	protected void buildGraph(Frame frame, Instruction i)
	{
		if (!isBuildGraph())
			return;
		
		if (i instanceof InvokeInstruction)
		{
			if (i instanceof InvokeStatic)
				return;
			
			InvokeInstruction ii = (InvokeInstruction) i;
			
			List<Method> methods = ii.getMethods();
			if (methods.isEmpty())
				return;
			
			for (Method m : methods)
				graph.addEdge(frame.nonStatic, m, EdgeType.INVOKE);
		}
		else if (i instanceof FieldInstruction)
		{
			FieldInstruction fi = (FieldInstruction) i;
			
			if (fi.getMyField() == null)
				return;
			
			EdgeType type = fi instanceof GetFieldInstruction ? EdgeType.GETFIELD : EdgeType.SETFIELD;
			graph.addEdge(frame.nonStatic, fi.getMyField(), type);
		}
	}
	
	public Graph getGraph()
	{
		return graph;
	}
}
