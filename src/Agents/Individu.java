package Agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.*;


public class Individu extends Agent {
	// List of other agents
	private AID[] otherAgents;
	// Iterate over all agents
	private int k = 0; 
	
	// Purposes
	private String ville_arrive;
	private String ville_depart;
	private int voiture; //Level of the car
	private int heure_depart;
	private int travel_time;
	private int nb_places; //Number of places available
	private int prix; //Price of one place
	private String[] attributs = new String[6];
	private String str_attributs; //Concatenation of all attributes
	
	// Constraints
	private int money_max; //Maximum money the agent is willing to pay for the ride
	private int car_min; //Minimum level of car the agent want to be passenger in
	private int time_max; //Maximum travel time
	
	// Current statut of the agent : he start neither driver nor passenger
	private boolean is_driver = false;
	private boolean is_passenger = false;
	
	// Vector of current passengers
	private Vector<AID> passengers = new Vector<AID>();
	// Vector of agents who refused the proposition
	private Vector<AID> refusals = new Vector<AID>();
	
	// Put agent initializations here
	protected void setup() {
		System.out.println("Agent "+getAID().getName()+" initialisation.");
		
		// Get arguments
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			System.out.println("pass");
			// Buts
			ville_depart = (String) args[0];
			ville_arrive = (String) args[1];
			voiture = Integer.parseInt((String) args[2]);
			heure_depart = Integer.parseInt((String) args[3]);
			travel_time = Integer.parseInt((String) args[4]);
			nb_places = Integer.parseInt((String) args[5]);
			prix = voiture * 100 / nb_places;
			
			// Constraints
			money_max = Integer.parseInt((String) args[6]);
			time_max = Integer.parseInt((String) args[7]);
			car_min = Integer.parseInt((String) args[8]);
			
			//fill the array with the attributes (to communicate with other agents)
			attributs[0] = ville_depart;
			attributs[1] = ville_arrive;
			attributs[2] = Integer.toString(voiture);
			attributs[3] = Integer.toString(heure_depart);
			attributs[4] = Integer.toString(travel_time);
			attributs[5] = Integer.toString(prix);
			
			//Convert the array into a string (to communicate)
			str_attributs = String.join(",", attributs);
			System.out.println(str_attributs);
			
		}

		
		// Register the agent in the DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("covoiturage");
		sd.setName("JADE-covoiturage");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		// Add a TickerBehaviour that ask the DF the AIDs of other agents
		// and make a proposition of codriving one agent at the time
		addBehaviour(new TickerBehaviour(this, 8000) {
			protected void onTick() {
				// Update the list of seller agents
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("covoiturage");
				template.addServices(sd);
				try {
					DFAgentDescription[] result = DFService.search(myAgent, template); 
					// System.out.println("Found the following seller agents:");
					otherAgents = new AID[result.length];
					System.out.println(result.length);
					for (int i = 0; i < result.length; ++i) {
						if(!(result[i].getName().equals(getAID()))) {
							otherAgents[i] = result[i].getName();
							//System.out.println(otherAgents[i].getName());
						}
					}
					//System.out.println(otherAgents[1]);
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}
				
				//Tell the current role of the agent
				if(is_driver == true) {
					System.out.println(getAID().getName() + " is a driver");
					System.out.println(getAID().getName() + " has got as passengers " + passengers);
				}
				if(is_passenger == true) {
					System.out.println(getAID().getName() + " is a passenger");
				}
				
				// Propose to other agents a seat
				if(is_passenger == false) {
					myAgent.addBehaviour(new Proposition());
				}
			}
		} );
		
		// Continuously answer propositions of other agents
		addBehaviour(new AnswerProposition());
		
	}
	
	// Put agent clean-up operations here
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Printout a dismissal message
		System.out.println("Agent "+getAID().getName()+" terminating.");
	}
	
	
	// Get the list of all other agents
	private class Request_DF extends jade.core.behaviours.OneShotBehaviour {
		public void action() {
			//Agent description
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd2 = new ServiceDescription();
			sd2.setType("covoiturage");
			template.addServices(sd2);
			
			try {
				//Register other agents
				DFAgentDescription[] result = DFService.search(myAgent, template);
				System.out.println("Found the following agents:");
				otherAgents = new AID[result.length];
				for (int i = 0; i < result.length; ++i) {
					//does not add himself
					if(!(result[i].getName().equals(getAID()))) {
						otherAgents[i] = result[i].getName();
						// System.out.println(otherAgents);
					}
				}
				// System.out.println("otherAgents : " + otherAgents);
			}
			catch (FIPAException fe) {
				fe.printStackTrace();
			}
	
		}
	}
	
	/**
	   
	 */
	private class AnswerProposition extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null && is_passenger == false && is_driver == false) {
				// Look the proposition
				String Content = msg.getContent();
				String[] Content_array = Content.split(",");
				ACLMessage reply = msg.createReply();
				reply.setConversationId("covoiturage-proposal");
				
				// If the proposition validate the constraints of the agent, accept it
				/*
				System.out.println(ville_depart.equals(Content_array[0]));
				System.out.println(ville_arrive.equals(Content_array[1]));
				System.out.println(heure_depart == Integer.parseInt(Content_array[3]));
				System.out.println(time_max >= Integer.parseInt(Content_array[4])); 
				System.out.println(car_min <= Integer.parseInt(Content_array[2]));
				System.out.println(money_max >= Integer.parseInt(Content_array[5]));
				*/
				
				if(ville_depart.equals(Content_array[0]) &&
						ville_arrive.equals(Content_array[1]) && 
						heure_depart == Integer.parseInt(Content_array[3]) && 
						time_max >= Integer.parseInt(Content_array[4]) 
						&& car_min <= Integer.parseInt(Content_array[2]) &&
						money_max >= Integer.parseInt(Content_array[5])) {
					System.out.println("Proposition acceptee");
					is_passenger = true;
					reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
					reply.setContent(getAID().getLocalName());
				} else {
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("NO");
				}
				// Send the reply
				myAgent.send(reply);
			} else {
				block();
			}
		}
	}
	
	
	/**
	   
	 */
	private class Proposition extends Behaviour {
		private int step = 0;
		private MessageTemplate mt; // The template to receive replies
		
		public void action() {
			switch(step) {
			case 0:
				//Send a proposition to one agent
				ACLMessage prop = new ACLMessage(ACLMessage.PROPOSE);
				prop.addReceiver(otherAgents[k]);
				prop.setContent(str_attributs);
				prop.setReplyWith("prop"+System.currentTimeMillis()); // Unique value
				//System.out.println(otherAgents[k]);
				//System.out.println(str_attributs);
				myAgent.send(prop);
				k++;
				if(k == otherAgents.length) {
					k = 0;
				}
				//Prepare the message template for answer
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("covoiturage-proposal"),
						MessageTemplate.MatchInReplyTo(prop.getReplyWith()));
				// mt = MessageTemplate.MatchConversationId("covoiturage-proposal");
				step = 1;
				break;
			case 1:
				//Receive answser of proposition
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					System.out.println("msg non null");
					if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL){
						// Proposition accepted
						System.out.println("ACCEPTED");
						is_driver = true;
						
						// Add the agent in his list of passengers
						passengers.addElement(msg.getSender());
						
						// Lose one seat
						nb_places = nb_places - 1;
						
						step = 2;
					} else if (msg.getPerformative() == ACLMessage.REFUSE) {
						// Proposition rejected
						refusals.add(msg.getSender());
						step = 2;
					}
				} else {
					block();
				}
				break;
			}
		}
		
		public boolean done() {
			return(step == 2);
		}
	}
}
	
