
help:
	@echo
	@echo 'Usage: make {clean|build|deploy|help}'
	@echo
	@echo 'Targets:'
	@echo '  clean        Remove stale files, etc.'
	@echo '  build        Clean and create boot jar file.'
	@echo '  deploy       Clean, build, and deploy jar to clojars.'
	@echo

clean:
	rm -f boot
	rm -rf resources/*
	(cd boot-classloader; lein clean)
	lein clean

build: clean
	mkdir -p resources
	(cd boot-classloader; lein uberjar)
	cp boot-classloader/target/boot-classloader-*-standalone.jar resources
	(cd resources; echo `ls boot-classloader-*-standalone.jar` > boot-classloader-resource-path)

deploy: build
	lein push
