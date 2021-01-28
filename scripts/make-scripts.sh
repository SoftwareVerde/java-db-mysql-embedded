#!/bin/bash

echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"\$@\"\n" > out/run.sh
echo -e "java -jar bin\\main.jar %*" > out/run.bat
chmod 770 out/*.sh

