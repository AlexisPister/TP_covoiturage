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
import java.util.concurrent.ThreadLocalRandom;


public class Individu extends Agent {
	// List of other agents
	private AID[] otherAgents;
	// Number of tickerbehaviour executed
	private int n = 0; 
	// [MODELE 2] Indice of comparaison between other ads
	private int l = 0;
	
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
	
	// [Modele 2] List of ads
	private Vector<String[]> Annonces = new Vector<String[]>();
	
	// [Modele 3] Aimed agents to send proposition
	private Vector<AID> aimedAgents = new Vector<AID>();
	
	// Put agent initializations here
	protected void setup() {
		System.out.println("Agent "+getAID().getName()+" initialisation.");
		
		// Get arguments
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			// Buts
			ville_depart = (String) args[0];
			ville_arrive = (String) args[1];
			voiture = Integer.parseInt((String) args[2]);
			heure_depart = Integer.parseInt((String) args[3]);
			travel_time = Integer.parseInt((String) args[4]);
			nb_places = Integer.parseInt((String) args[5]);
			prix = Integer.parseInt((String) args[6]);
			
			// Constraints
			money_max = Integer.parseInt((String) args[7]);
			time_max = Integer.parseInt((String) args[8]);
			car_min = Integer.parseInt((String) args[9]);
			
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
		
		
		// Sleep to have the time to start sniffer agent
		try{
		    Thread.sleep(5000);
		}
		catch(InterruptedException ex){
		    Thread.currentThread().interrupt();
		}
		
		
		// Ask the DF AIDs of other agents
		addBehaviour(new Request_DF());
		
		
		// [MODELE 2/3]
		addBehaviour(new Get_annonces());
		addBehaviour(new Send_annonce());
		
		// Print current status of agent and potentially send a proposition depending of status
		addBehaviour(new TickerBehaviour(this, 5000) {
			protected void onTick() {
				//Tell the current role of the agent
				System.out.print("( n = " + n + " ) ");
				if(is_driver == true) {
					System.out.println(getAID().getName() + " is a driver");
					System.out.print(getAID().getName() + " has got as passengers ");
					for(int i = 0; i < passengers.size(); ++i) {
						System.out.print(passengers.elementAt(i).getLocalName() + " , ");
					}
					System.out.print('\n');
				}
				if(is_passenger == true) {
					System.out.println(getAID().getName() + " is a passenger");
				}
				if(is_driver == false && is_passenger == false) {
					System.out.println(getAID().getName() +
							" is still searching a coalition");
				}
				
				
				// Propose to other agents a seat 
				if(is_passenger == false & nb_places > 0) {
					//[MODELE 1/2]
					//myAgent.addBehaviour(new Proposition0());
					
					//[MODELE 3]
					myAgent.addBehaviour(new Proposition2());
				}
				
				if(n == 20) {
					try{
					    Thread.sleep(2000);
					} catch(InterruptedException ex){
					    Thread.currentThread().interrupt();
					}
					if(is_driver == true) {
						System.out.print(getAID().getLocalName() + " a créé une"
								+ " coalition avec ");
						for(int i = 0; i < passengers.size(); ++i) {
							System.out.print(passengers.elementAt(i).getLocalName() + " , ");
						}
						System.out.print('\n');
					} else if(is_passenger == false & is_driver == false) {
						is_driver = true;
						System.out.println(getAID().getLocalName() +
								" décide de voyager tout seul");
					}
					myAgent.doDelete();
				}
				
				n += 1;
				
				// Every 3 turns, the agent lower his exceptations
				if(n%3 == 0) {
					if(l < Annonces.size() - 1) {
						l += 1;
					}
				}
				
			}
		} );
		
//		// [MODELE 1] Continuously answer propositions of other agents
//		addBehaviour(new AnswerProposition0());
		
		// [MODELE 2/3]
		addBehaviour(new AnswerProposition1());
		
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
		// System.out.println("Agent "+getAID().getName()+" terminating.");
	}
	
	
	// Tell if ad 1 is better than ad 2
	// Compare price (weight of 1), level of car (weight of 20)
	// and travel time (weight of 5)
	public boolean bestAnnonce(String[] Ann1, String[] Ann2) {
		int diff_car = Integer.parseInt(Ann1[2]) -  Integer.parseInt(Ann2[2]);
		int diff_time = Integer.parseInt(Ann2[4]) -  Integer.parseInt(Ann1[4]);
		int diff_price = Integer.parseInt(Ann2[5]) - Integer.parseInt(Ann1[5]);
		
		int Indicateur = diff_car * 20 + diff_time * 5 + diff_price;
		if(Indicateur > 0) {
			return(true);
		}else {
			return(false);
		}
	}
	
	
	// Get the list of all other agents
	private class Request_DF extends jade.core.behaviours.OneShotBehaviour {
		private int j = 0;
		public void action() {
			//Agent description
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd2 = new ServiceDescription();
			sd2.setType("covoiturage");
			template.addServices(sd2);
			
			try {
				//Register other agents
				DFAgentDescription[] result = DFService.search(myAgent, template);
				// System.out.println("Found the following agents:");
				otherAgents = new AID[result.length - 1];
				for (int i = 0; i < result.length; ++i) {
					//does not add himself
					if(!(result[i].getName().equals(getAID()))) {
						otherAgents[j] = result[i].getName();
						// System.out.println(otherAgents[j]);
						j = j + 1;
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
	   Modele 2
	 */
	private class AnswerProposition1 extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				ACLMessage reply = msg.createReply();
				reply.setConversationId("covoiturage-proposal");
				
				if(is_passenger == true || is_driver == true) {
					// Refuse if driver or passenger
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("NO");
				} else {
					// Else look the proposition
					String Content = msg.getContent();
					String[] Content_array = Content.split(",");
					
					// TEST if there is a better ad in his vector Annonces
					// l represent the selectivity of the agent
					// Refuse if agent has got the best offer
					if((ville_depart.equals(Content_array[0]) &&
							ville_arrive.equals(Content_array[1]) && 
							heure_depart < Integer.parseInt(Content_array[3]) + 1 &&
							heure_depart > Integer.parseInt(Content_array[3]) - 1 &&
							time_max >= Integer.parseInt(Content_array[4]) &&
							car_min <= Integer.parseInt(Content_array[2]) &&
							money_max >= Integer.parseInt(Content_array[5])) &&
							!bestAnnonce(Annonces.get(l), Content_array) &&
							!Arrays.equals(Annonces.get(0), attributs)) {
						System.out.println(getAID().getLocalName() +
								"a rejoint la coalition de " + msg.getSender().getLocalName());
						reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
						reply.setContent(getAID().getLocalName());
						is_passenger = true;
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("NO");
					}
				}
				// Send the reply
				myAgent.send(reply);
			} else {
				block();
			}
		}
	}
	
	/**
	   Send ad to all other agents
	 */
	private class Send_annonce extends OneShotBehaviour {
		public void action() {
			ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
			for (int i = 0; i < otherAgents.length; ++i) {
				cfp.addReceiver(otherAgents[i]);
			} 
			cfp.setContent(str_attributs);
			cfp.setReplyWith("prop"+System.currentTimeMillis()); // Unique value // Unique value
			myAgent.send(cfp);
		}
	}
	
	/**
	   Receive ads of all other agents and sort them
	 */
	private class Get_annonces extends CyclicBehaviour {
		private boolean sorted = false;
		private int j = 0;
		
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				// Save the ad if it validates the constraints of agent
				String Content = msg.getContent();
				String[] Content_array = Content.split(",");
				
				if(ville_depart.equals(Content_array[0]) &&
						ville_arrive.equals(Content_array[1]) && 
						heure_depart < Integer.parseInt(Content_array[3]) + 1 &&
						heure_depart > Integer.parseInt(Content_array[3]) - 1 &&
						time_max >= Integer.parseInt(Content_array[4]) &&
						car_min <= Integer.parseInt(Content_array[2]) &&
						money_max >= Integer.parseInt(Content_array[5])) {
					Annonces.add(Content_array);
					// [Modele 3] List of annonces
					aimedAgents.add(msg.getSender());
				}
				
				j = j+1;
				
				// Sort the ads from best to worst when all ads received
				if(j == otherAgents.length) {
					while(sorted == false) {
						sorted = true;
						for(int i = 0; i < Annonces.size()-1; ++i) {
							if(bestAnnonce(Annonces.get(i+1), Annonces.get(i))) {
								Collections.swap(Annonces, i, i+1);
								sorted = false;
							}
						}
					}
					j = 0;
				}
			} else {
				block();
			}
		}
	}
	
	
	
	
	
	/**
	   Answer proposition for modele 1
	 */
	private class AnswerProposition0 extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				ACLMessage reply = msg.createReply();
				reply.setConversationId("covoiturage-proposal");
				
				if(is_passenger == true || is_driver == true) {
					// Refuse if driver or passenger
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("NO");
				} else {
					// Else look the proposition
					String Content = msg.getContent();
					String[] Content_array = Content.split(",");
					
					
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
							heure_depart < Integer.parseInt(Content_array[3]) + 1 &&
							heure_depart > Integer.parseInt(Content_array[3]) - 1 &&
							time_max >= Integer.parseInt(Content_array[4]) &&
							car_min <= Integer.parseInt(Content_array[2]) &&
							money_max >= Integer.parseInt(Content_array[5])) {
						System.out.println(getAID().getLocalName() +
								"a rejoint la coalition de " + msg.getSender().getLocalName());
						reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
						reply.setContent(getAID().getLocalName());
						is_passenger = true;
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("NO");
					}
				}
				// Send the reply
				myAgent.send(reply);
			} else {
				block();
			}
		}
	}
	
	
	/**
	   Send proposition to one agent at the time
	 */
	private class Proposition0 extends Behaviour {
		private int step = 0;
		private int k;
		private MessageTemplate mt; // The template to receive replies
		// Vector of indexes of the agents already asked
		private Vector<Integer> asked_agents = new Vector<Integer>();
		
		public void action() {
			switch(step) {
			case 0:
				// Choose one agent randomly
				k = ThreadLocalRandom.current().nextInt(0, otherAgents.length);
				while(asked_agents.contains(k)) {
					k = ThreadLocalRandom.current().nextInt(0, otherAgents.length);
				}
				// Don't ask the same next turn
				asked_agents.addElement(k);
				
				//Send proposition
				ACLMessage prop = new ACLMessage(ACLMessage.PROPOSE);
				prop.addReceiver(otherAgents[k]);
				prop.setContent(str_attributs);
				prop.setReplyWith("prop"+System.currentTimeMillis()); // Unique value
				//System.out.println(otherAgents[k]);
				//System.out.println(str_attributs);
				myAgent.send(prop);
				if(asked_agents.size() == otherAgents.length) {
					// Can choose any agents again
					asked_agents.clear(); 
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
					// System.out.println("msg non null");
					if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL){
						// Proposition accepted
						// System.out.println("ACCEPTED");
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
	
	
	
	
	/**
	   Send proposition to one agent at the time [MODELE 3]
	   aim agents with same constraints
	 */
	private class Proposition2 extends Behaviour {
		private int step = 0;
		private int k;
		private MessageTemplate mt; // The template to receive replies
		// Vector of indexes of the agents already asked
		private Vector<Integer> asked_agents = new Vector<Integer>();
		
		public void action() {
			switch(step) {
			case 0:
				// Choose one agent randomly
				k = ThreadLocalRandom.current().nextInt(0, aimedAgents.size());
				while(asked_agents.contains(k)) {
					k = ThreadLocalRandom.current().nextInt(0, aimedAgents.size());
				}
				// Don't ask the same next turn
				asked_agents.addElement(k);
				
				//Send proposition
				ACLMessage prop = new ACLMessage(ACLMessage.PROPOSE);
				prop.addReceiver(aimedAgents.get(k));
				prop.setContent(str_attributs);
				prop.setReplyWith("prop"+System.currentTimeMillis()); // Unique value
				//System.out.println(otherAgents[k]);
				//System.out.println(str_attributs);
				myAgent.send(prop);
				if(asked_agents.size() == aimedAgents.size()) {
					// Can choose any agents again
					asked_agents.clear(); 
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
					// System.out.println("msg non null");
					if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL){
						// Proposition accepted
						// System.out.println("ACCEPTED");
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
	
