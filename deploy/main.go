package main

import (
	"encoding/xml"
	"log"
	"os"
	"os/exec"
	"sync"
)

var modules = []string{"oauth", "projects"}

type Settings struct {
	Servers []Server `xml:"servers>server"`
}

type Server struct {
	Id string `xml:"id"`
}

func main() {
	
	settingsFile, err := os.ReadFile("settings.xml")

	if err != nil {
		log.Fatal(err)
	}
	
	var settings Settings

	if err:= xml.Unmarshal(settingsFile, &settings); err != nil {
		log.Fatal(err)
	}

	serverName := settings.Servers[0].Id
    
	for _, e := range modules {
		data, err := os.ReadFile(e + "/pom.xml")

		if err != nil {
			log.Fatal(err)
		}

		var project MavenProject

        if err := xml.Unmarshal(data, &project); err != nil {
            log.Fatalf("unable to unmarshal pom file. Reason: %s", err)
        }
		
		project.Build.PluginManagement.Plugins = append(project.Build.PluginManagement.Plugins, Plugin {
			XMLName: xml.Name{ Local: "plugin" },
			GroupId: "org.apache.tomcat.maven",
			ArtifactId: "tomcat7-maven-plugin",
			Version: "2.2",
			Configuration: ConfigurationMap {
				m: map[string]string {
					"url": "http://localhost:8080/manager/text",
					"server": serverName,
					"path": "/" + e,
				},
			},
		})

		res, err := xml.MarshalIndent(&project, "", "  ")

		if err != nil {
			log.Fatal(err)
		}

		os.WriteFile(e + "/pom.xml", res, 0644)
    }
	
	var wg sync.WaitGroup

	for _, e := range modules {
		wg.Add(1)
		go worker(&wg, e)
	}
	wg.Wait()
}

func worker(wg *sync.WaitGroup, folder string) {
	defer wg.Done()

	cmd := exec.Command("mvn", "clean", "install")
	cmd.Dir = folder
	out, err := cmd.Output()

	if err != nil {
		log.Fatal(err)
	}

	log.Println(string(out))

	cmd = exec.Command("mvn", "compile")
	cmd.Dir = folder
	out, err = cmd.Output()

	if err != nil {
		log.Fatal(err)
	}

	log.Println(string(out))

	cmd = exec.Command("mvn", "package")
	cmd.Dir = folder
	out, err = cmd.Output()

	if err != nil {
		log.Fatal(err)
	}

	log.Println(string(out))
}