package ca.polymtl.inf8480.tp1.server;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import ca.polymtl.inf8480.tp1.shared.ServerInterface;

import java.util.*;
import java.io.*;
import java.nio.file.Files;

public class Server implements ServerInterface {

	public static void main(String[] args) {
		Server server = new Server();
		server.run();
	}

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
		
			ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(this, 0);

			Registry registry = LocateRegistry.getRegistry();
			registry.rebind("server", stub);
			System.out.println("Server ready.");
		
		
		} catch (ConnectException e) {
			
			System.err
					.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
		
		try{
			
			properties = new Properties();
			properties.loadFromXML(new FileInputStream(folder + "/properties.xml"));
			
		} catch ( IOException e) {
			System.err.println("Erreur loading properties : " + e.getMessage());
		}
		
		try{
			clientsTotal =  Integer.parseInt( properties.getProperty("clientsTotal"));
		}
		catch(NumberFormatException e){}
		catch(NullPointerException e){}
		
	}
	
	public Server() {
		super();
	}
	
	// useful for ID generation
	// during first session, begins at 1 so 0 is reserved
	private int clientsTotal = 1;
	// retains data between sessions, mainly the association <string filename, int as string checksum>
	private java.util.Properties properties;
	// directory used to store data
	private String folder = "./serverFiles";
	// directory that stores the managed files
	private String storage = folder + "/cloud_storage";
	
	private void syncProperties(){
		try{
			properties.storeToXML(new FileOutputStream(folder + "/properties.xml"), "");
		} catch ( IOException e) {
			System.err.println("Error exporting properties : " + e.getMessage());
		}
	}
	
	
	/*
		Génère un identifiant unique pour le client.
		Celui-ci est sauvegardé dans un fichier local et est	retransmis au serveur lors de l'appel à lock() ou  push().
		Le client doit demander un identifiant lors de sa première interaction avec le serveur
		- Retourne un int correspondant à l'ID. -
	*/
	@Override
	public int CreateClientID() throws RemoteException{
		
		int ID = clientsTotal;
		clientsTotal += 1;
		
		// so we can have a different id each time even if the server closes
		properties.setProperty("clientsTotal", Integer.toString(clientsTotal));
		syncProperties();
		
		return ID;
	}
	
	/*
		Crée un fichier vide sur le serveur avec le nom spécifié.
		Si un fichier portant ce nom existe déjà, l'opération échoue.
		- Retourne un booleen pour indiquer si reussi. -
	*/
	@Override
	public boolean create(String nom) throws RemoteException{
		
		// attempts creating the file
		File f = new File(storage +"/"+nom);
		try{
			if( !f.exists() ){
				f.createNewFile();
				
				// some book keeping : the MD5 and current owner of file
				properties.setProperty(nom + "_MD5", Integer.toString(0));
				properties.setProperty(nom + "_owner", Integer.toString(0));
				syncProperties();
				
				return true;
			}
		}
		catch(IOException e){	}
		
		return false;
	}
	
	/*
		Retourne la liste des fichiers présents sur le serveur.
		Pour chaque fichier, le nom et l'identifiant du client possédant le verrou (le cas échéant) est retourné.
		- Retourne un tableau d'entrees : String nomFichier, int client ayant le verrou -
	*/
	@Override
	public String[][] list() throws RemoteException{
		
		// we get a liting of files in storage
		File[] files = new File(storage).listFiles();
		String[][] result = new String[files.length][];
		
		// for each file we add its info to result
		for (int i=0; i< files.length; i++) {
			
			File file = files[i];
			String name = file.getName();
			String owner = properties.getProperty(name + "_owner");
			
			// if there is an owner (clientID != 0) we specify it 
			if(Integer.parseInt(owner)!=0){
				// used format is {name [, owner]}
				result[i] = new String[2];
				result [i][1] = owner;
			}
			else{
				result[i] = new String[1];
			}
			result[i][0] = name;
		}
		
		
		return result;
	}
	
	
	/*
		Permet de récupérer les noms et les contenus de tous les fichiers du serveur.
		Le client appelle cette fonction pour synchroniser son répertoire local avec celui du serveur.
		Les fichiers existants seront écrasés et remplacés par les versions du le serveur.
		- Retourne une liste d'entrees : String nomFichier, String contenu -
	*/
	@Override
	public String[][] syncLocalDirectory() throws RemoteException{
		// we get a liting of files in storage
		File[] files = new File(storage).listFiles();
		String[][] result = new String[files.length][2];
		
		// for each file we add its info to result
		for (int i=0; i< files.length; i++) {
			
			File file = files[i];
			String name = file.getName();
			String content= "";
			try{
				 content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
			}
			catch(Exception e ){
				System.err.println("Error getting file content in syncLocalDirectory : " + e.getMessage());
			}
			
			result[i][0] = name;
			result [i][1] = content;
		}		
		
		return result;
	}
	
	/*
		Demande au serveur d'envoyer la dernière version du fichier spécifié.
		Le client passe également la somme de contrôle du fichier qu'il possède.
		Si le client possède une somme de contrôle égale à celle du serveur, le fichier n’est pas retourné car il est déjà à jour dans le client.
		Si le client ne possède pas le fichier, il doit spécifier une somme de contrôle nulle pour forcer le serveur à lui envoyer le fichier.
		Le fichier est écrit dans le répertoire local courant.
		- Retourne un tableau contenant le fichier si besoin : String contenu -
	*/
	@Override
	public String[] get(String nom, int checksum) throws RemoteException{
		
		return null;
	}
	
	/*
		Demande au serveur de verrouiller le fichier spécifié.
		La dernière version du fichier est écrite dans le répertoire local courant ( la somme de contrôle est aussi utilisée pour éviter un transfert inutile).
		L'opération échoue si le fichier n’existe pas ou il est déjà verrouillé par un autre client.
		Le client doit recevoir l’ID du client qui détient le verrou.
		- Retourne le contenu du fichier, : String contenu, int  as String l'ID du client (celui fourni si le lock reussi) -
	*/
	@Override
	public String[] lock(String nom, int clientid, int checksum) throws RemoteException{

		return null;
	}
	
	
	/*
		Envoie une nouvelle version du fichier spécifié au serveur.
		L'opération échoue si le fichier n'avait pas été verrouillé par le client préalablement.
		Si le push réussit, le contenu envoyé par le client remplace le contenu qui était sur le serveur auparavant et le fichier est déverrouillé.
		- Retourne un booleen pour indiquer si reussi. -
	*/
	@Override
	public boolean push(String nom, String contenu, int clientid) throws RemoteException{
		return false;
	}
	
	
	
}