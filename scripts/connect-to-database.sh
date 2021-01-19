#!/bin/bash

USER='root'
PASSWORD=''
DATABASE='example'
PORT='3306'
HOST='127.0.0.1'

QUERY="$1"

echo -n 'MySQL Root Password: '
read -s PASSWORD

if [ -z "${QUERY}" ]; then
    mysql --binary-as-hex -u ${USER} -h ${HOST} -P${PORT} -p${PASSWORD} ${DATABASE}
else
    mysql --binary-as-hex -u ${USER} -h ${HOST} -P${PORT} -p${PASSWORD} ${DATABASE} -e "${QUERY}"
fi

