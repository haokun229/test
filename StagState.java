import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * this is a StagState class that maintain the state of the game
 */
public class StagState {

  private Graph locations;    // locations graph of the game
  private Graph unplacedCharacter;  // unplaced characters in the game
  private Graph unplacedFurniture;  // unplaced furniture in the game
  private Graph unplacedArtefacts;  // unplaced artefacts in the game
  private Graph path;          // path between the locations in the game
  List<Action> actions;        // actions can be act by player
  Map<String, Player> players; // multi-player maintain


  /**
   * constructor, initial the game by config file
   * @param entityFileName
   * @param actionFileName
   */
  public StagState(String entityFileName, String actionFileName) {


    try {   // parse graph
      Parser parser = new Parser();
      FileReader reader = new FileReader(entityFileName);  // reader of file
      parser.parse(reader);  // initial parser with reader
      ArrayList<Graph> graphs = parser.getGraphs();
      ArrayList<Graph> subGraphs = graphs.get(0).getSubgraphs();
      locations = subGraphs.get(0);    // get all locations
      path = subGraphs.get(1);         // get path

      for (Graph location: locations.getSubgraphs()) { // parse unplaced entity, unplaced entity is subgraph of locations
        if (Objects.equals("unplaced", location.getNodes(false).get(0).getId().getId())) {  // find unplaced graph
          for (Graph thing: location.getSubgraphs()) {  // iterator to visit different type unplaced entity
            if (Objects.equals("characters", thing.getId().getId())) {
              unplacedCharacter = thing;
            }
            else if (Objects.equals("artefacts", thing.getId().getId())) {
              unplacedArtefacts = thing;
            }
            else {
              unplacedFurniture = thing;
            }
          }
        }
      }

    } catch (FileNotFoundException | ParseException e) {
      System.out.println(e);
    }

    try {  // parse actions
      JSONParser parser = new JSONParser();
      FileReader reader = new FileReader(actionFileName);  // reader file
      actions = new ArrayList<>();

      JSONObject jsonObject = (JSONObject) parser.parse(reader);  // initial jsonObject with reader
      JSONArray jsonArray = (JSONArray) jsonObject.get("actions");  // get actions jsonArray
      for (Object object: jsonArray) { // iterator to visit different action

        // initial different action's element
        Set<String> triggers = new HashSet<>();
        Set<String> subjects = new HashSet<>();
        Set<String> consumed = new HashSet<>();
        Set<String> produced = new HashSet<>();

        JSONObject behaviours = (JSONObject) object;

        // parse different action's element
        parseElement(behaviours, "triggers", triggers);
        parseElement(behaviours, "subjects", subjects);
        parseElement(behaviours, "consumed", consumed);
        parseElement(behaviours, "produced", produced);
        String narration = (String) behaviours.get("narration");  // parse narration
        Action action = new Action(triggers, subjects, consumed, produced, narration); // create an action use elements
        actions.add(action);
      }

    } catch (IOException | org.json.simple.parser.ParseException e) {
      System.out.println(e);
    }

    players = new HashMap<>();   // initial multi-player
  }

  /**
   * find player by name
   * @param name
   * @return
   */
  public Player findPlayer(String name) {
    if (players.containsKey(name)) {  // player already join the game, return player
      return players.get(name);
    }
    else {  // first join the game
      Player player = new Player(name, locations.getSubgraphs().get(0)); // initial the player in the start player
      players.put(name, player);   // add to multi-player maintain
      addEntityToGraph(player.getPosition(), player); // add player to graph
      return player;
    }
  }


  /**
   * create an entity node
   * @param thing entity
   * @return
   */
  private Node createEntityNode(Entity thing) {
    Node node = new Node();
    Id nid = new Id();    // create an Id for entity
    nid.setId(thing.getName());     // use name as id
    node.setId(nid);
    node.setAttribute("description", thing.getDescription()); // add description to node
    return node;
  }

  /**
   * add an entity to current location graph
   * @param graph current location graph
   * @param thing entity
   */
  private void addEntityToGraph(Graph graph, Entity thing) {

    Graph entityGraph = null;
    for (Graph sub: graph.getSubgraphs()) {  // iterator to find target type graph
      if (Objects.equals(sub.getId().getId(), thing.getId())) {
        entityGraph = sub;
        break;
      }
    }

    if (entityGraph != null) {   // target type graph already in the graph
      entityGraph.addNode(createEntityNode(thing));  // add entity node to target type graph
    }
    else {  // target type graph not in the graph
      Graph subGraph = createEntityGraph(thing);   // create the target entity graph
      graph.addSubgraph(subGraph);  // add the target graph to current location graph
    }


  }

  /**
   * create an target type graph of entity
   * @param thing
   * @return
   */
  private Graph createEntityGraph(Entity thing) {
    Graph graph = new Graph();
    Id id = new Id();   // create an Id(type) for the target graph
    id.setId(thing.getId());  // use entity's type(id) as id
    graph.setId(id);
    graph.addGenericNodeAttribute("shape", thing.getShape()); // set graph's shape

    Node node = createEntityNode(thing);   // create entity node
    graph.addNode(node);  // add node to target graph
    return graph;
  }

  /**
   * player pick up something
   * @param player target player
   * @param name target entity
   * @return
   */
  public String pickUp(Player player, String name) {
    Node node = removeThingFromPosition(player.getPosition(), name);  // remove the entity from current location graph
    Artefacts artefacts = new Artefacts(name, node.getAttribute("description"));  // create a artefacts for the entity as detail
    player.pickUp(artefacts);  // put it to the player's inventory
    return "You pick up " + name;
  }

  /**
   * drop something from player
   * @param player target player
   * @param name target entity
   * @return
   */
  public String drop(Player player, String name) {
    Artefacts artefacts = player.drop(name, 1);  // drop entity from player's entity
    addEntityToGraph(player.getPosition(), artefacts); // add entity to current location graph
    return "You drop " + name;
  }


  /**
   * change player's location
   * @param player target player
   * @param pos target location
   * @return
   */
  public String gotoPosition(Player player, String pos) {
    if (!hasEdgeToPos(player.getPosition().getNodes(false).get(0).getId().getId(), pos)) {  // check target location valid or not
      return "You can't goto " + pos;
    }

    removeThingFromPosition(player.getPosition(), player.getName());  // remove the player from current location graph
    Graph nextPos = findGraphByNode(pos);  // find target location graph by name
    player.setPosition(nextPos);  // update location
    addEntityToGraph(player.getPosition(), player);  // add player to new location graph

    return look(player);


  }

  /**
   * look the current location of the player
   * @param player
   * @return
   */
  public String look(Player player) {
    String resp = "You are in ";

    String pos = player.getPosition().getNodes(false).get(0).getId().getId();  // current location name
    resp = resp + pos + " now.\n";

    resp = resp + "These following things are in these position:\n";
    for(Graph sub: player.getPosition().getSubgraphs()) {  // iterator to visit different type entity in the location
      String type = sub.getId().getId();
      resp = resp + type + ":\n";
      List<Node> nodes = sub.getNodes(false);
      for (Node node: nodes) {  // iterator to visit different entity
        if (Objects.equals(player.getName(), node.getId().getId())) {
          continue;
        }
        resp = resp + "\t" + node.getId().getId() + "\n";
      }
    }

    resp = resp + "You can goto to the following position: \n";

    List<String> adjacentPos = adjacentPosition(pos);   // get adjacent location can goto
    for (String adjacent: adjacentPos) {
      resp = resp + "\t" + adjacent + "\n";
    }
    return resp;
  }

  /**
   * get adjacent location can goto of the location
   * @param name location's name
   * @return
   */
  private List<String> adjacentPosition(String name) {
    ArrayList<String> adjacentPos = new ArrayList<>();

    ArrayList<Edge> edges = path.getEdges();   // get path edge

    for (Edge e : edges){  // iterator to visit each edge
      String source = e.getSource().getNode().getId().getId();  // get edge source name
      String target = e.getTarget().getNode().getId().getId();  // get edge target name
      if (Objects.equals(source, name)) {   // source match location's
        adjacentPos.add(target);  // add target name to adjacent locations
      }
    }

    return adjacentPos;
  }


  /**
   * find an location graph by location name
   * @param name location name
   * @return
   */
  private Graph findGraphByNode(String name) {
    for (Graph pos: locations.getSubgraphs()) {
      Node node = pos.getNodes(false).get(0); // get location node
      if (Objects.equals(name, node.getId().getId())) {  // location node name match location name
        return pos;
      }
    }
    return null;
  }

  /**
   * check location s to location t has a path or not
   * @param s source location
   * @param t target location
   * @return
   */
  private boolean hasEdgeToPos(String s, String t) {
    ArrayList<Edge> edges = path.getEdges();

    for (Edge e : edges){  // iterator to visit each path
      String source = e.getSource().getNode().getId().getId(); // get source name
      String target = e.getTarget().getNode().getId().getId(); // get target name
      if (Objects.equals(source, s) && Objects.equals(t, target)) { // source and target name match, has a path
        return true;
      }
    }

    return false;  // all path check, not find a path
  }


  /**
   * parse an action element by the given element type
   * @param behaviours action json object
   * @param element given element type
   * @param set element set
   */
  private void parseElement(JSONObject behaviours, String element, Set<String> set) {
    JSONArray triggers = (JSONArray) behaviours.get(element);
    for (Object obj: triggers) {
      set.add((String) obj);
    }
  }


  /**
   * check the given subject is a target type entity or not in the current location
   * @param graph  current location graph
   * @param subject given subject
   * @param target target type
   * @return
   */
  private boolean isEntity(Graph graph, String subject, String target) {
    for(Graph sub: graph.getSubgraphs()) { // iterator to visit each type graph
      String type = sub.getId().getId();  // type id
      if (!Objects.equals(type, target)) {
        continue;
      }
      for (Node node: sub.getNodes(false)){  // match the target type graph, iterator to visit each entity node
        if (Objects.equals(node.getId().getId(), subject)) {  // match entity name
          return true;
        }
      }
    }

    return false;  // none match the subject
  }

  /**
   * get artefacts set in an action's subjects
   * @param location current location graph
   * @param subjects action' subject
   * @return
   */
  private Set<String> getArtefactsTools(Graph location, Set<String> subjects) {
    Set<String> tools = new HashSet<>();

    Set<String> furniture = getFurniture(location, subjects);  // get furniture set

    for (String subject: subjects) { // iterator to check each subject
      if (!furniture.contains(subject) && !isEntity(location, subject, "characters")) {
        tools.add(subject);  // if a subject is not a furniture nor a character, it is a artefacts.
      }
    }

    return tools;
  }


  /**
   * get a furniture set in an action's subjects
   * @param location current location graph
   * @param subjects action subjects
   * @return
   */
  private Set<String> getFurniture(Graph location, Set<String> subjects) {
    Set<String> furniture = new HashSet<>();

    for (String subject: subjects) {  // iterator to check each subject
      if (isEntity(location, subject, "furniture")) {  // use isEntity to check is a furniture
        furniture.add(subject);
      }

    }
    return furniture;
  }

  /**
   * remove an entity from target type graph
   * @param graph target type graph
   * @param name entity name
   * @return
   */
  private Node removeThingFromGraph(Graph graph, String name) {
    List<Node> nodes = graph.getNodes(true);  // get all entity nodes of the target graph, true for modify in place
    Node remove = null;
    for (Node node: nodes) { // iterator to find entity
      if (Objects.equals(node.getId().getId(), name)) { // match the entity name
        remove = node;
        nodes.remove(remove); // remove node from graph
        return node;
      }
    }
    return null; // none match
  }

  /**
   * remove entity from current location graph
   * @param pos current location graph
   * @param name entity name
   * @return
   */
  private Node removeThingFromPosition(Graph pos, String name) {
    for (Graph sub: pos.getSubgraphs()) { // iterator different entity type graph to find entity
     Node node = removeThingFromGraph(sub, name);  // call removeThingFromGraph to try remove
     if (node != null) { // node is not null find and remove success
       return node;
     }
    }
    return null;  // none match
  }

  /**
   * check a entity is an unplaced object or not
   * @param unplaced unplaced type graph
   * @param name entity name
   * @return
   */
  private boolean isUnplacedObj(Graph unplaced, String name) {
    for (Node node: unplaced.getNodes(true)) {  // iterator to visit all unplaced entity node to check
      if (Objects.equals(node.getId().getId(), name)) {
        return true;  // node match the name, find
      }
    }
    return false; // none match
  }

  /**
   * process action define by action file
   * @param player
   * @param message
   * @return
   */
  public String processAction(Player player, String[] message) {
    String trigger = message[0]; // get trigger word
    for (Action action: actions) { // iterator to check action trigger
      if (action.hasTrigger(trigger)) {  // action has trigger word
        Set<String> subjects = new HashSet<>();

        for (int i = 1;  i < message.length; i++) {   // get player's subject
          subjects.add(message[i]);
        }

        if (!action.checkSubjects(subjects)) {  // compare to action's subject
          return  "Your subjects don't match the action need.";
        }

        subjects = action.getSubjects();
        Set<String> tools = getArtefactsTools(player.getPosition(), subjects);
        Set<String> furniture = getFurniture(player.getPosition(), subjects);

        if(!player.hasTools(tools)) {   // check player has artefacts to act or not
          return  "You don't have enough artefacts to act.";
        }

        Set<String> consumed = action.getConsumed();  // process consumed
        for (String consume: consumed) {  // iterator to process each consumed
          if (Objects.equals(consume, "health")) {  // consume health
            player.decreaseHealth(1);  // decrease player' health
            if (player.getHealth() == 0) {   // player run out health
              Set<String> inventory = player.getInventoryKey();
              for (String name: inventory) {    // iterator drop all thing to current location graph
                int num = player.getInventoryNum(name);  // get number of the thing in inventory
                Artefacts artefacts = player.drop(name, num); // drop all thing
                for (int i = 0; i < num; i++) {  // add all thing to current location graph
                  addEntityToGraph(player.getPosition(), artefacts);
                }
              }
              players.remove(player.getName());  // reset player
              return "You lose your life, drop every thing to the current location and return to start";
            }
          }
          else if (isEntity(player.getPosition(), consume, "furniture")) { // consume furniture
            removeThingFromPosition(player.getPosition(), consume); // remove furniture from location graph
          }
          else {  // consume player's artefacts
            player.drop(consume, 1);   // drop a artefacts
          }

        }

        Set<String> produced = action.getProduced();  // process produced
        for (String prod : produced) { // iterator to process produced
          if (Objects.equals(prod, "health")) { // produce health
            player.increaseHealth(1);  // increase player's health
          }
          else {
            // check produced is a type of unplaced entity or not
            // if it is a unplaced entity
            // remove unplaced entity from unplaced graph
            // add unplaced entity to location graph
            if (isUnplacedObj(unplacedCharacter, prod)) {
              Node node = removeThingFromGraph(unplacedCharacter, prod);
              Character character = new Character(prod, node.getAttribute("description"));
              addEntityToGraph(player.getPosition(), character);
            }
            else if (isUnplacedObj(unplacedArtefacts, prod)) {
              Node node = removeThingFromGraph(unplacedArtefacts, prod);
              Artefacts artefacts = new Artefacts(prod, node.getAttribute("description"));
              addEntityToGraph(player.getPosition(), artefacts);
            }
            else if (isUnplacedObj(unplacedFurniture, prod)) {
              Node node = removeThingFromGraph(unplacedFurniture, prod);
              Furniture furniture1 =  new Furniture(prod, node.getAttribute("description"));
              addEntityToGraph(player.getPosition(), furniture1);
            }
            else {
              // produced doesn't match any unplaced entity, it should be a location's name
              // it should produce a path from current location to the produce location
              Node src = player.getPosition().getNodes(false).get(0);  // current location node
              PortNode source = new PortNode(src); // source portNode
              Node tar = findLocationNode(prod);  // find produced location node
              PortNode target = new PortNode(tar); // target portNode
              Edge edge = new Edge(source, target, 2); // create a edge from current location to produced location
              path.addEdge(edge);  // add edge to path
            }

          }


        }

        return action.getNarration();

      }
    }

    return "Your command is not support.";  // nothing action action match

  }

  /**
   * find location node by name
   * @param name locathion name
   * @return
   */
  private Node findLocationNode(String name) {
    for (Graph location: locations.getSubgraphs()) { // iterator to visit each location
      Node loc = location.getNodes(false).get(0);  // get location node
      if (Objects.equals(loc.getId().getId(), name)) { // compare the node and name
        return loc; // match
      }
    }
    return null;   // none match
  }

}
