# coding: utf8
from __future__ import unicode_literals

import hug
from hug_middleware_cors import CORSMiddleware
from polyglot.text import Text
import re
from nameparser import HumanName
import json

class TurPolyToken:
    def __init__(self, token, label):
        self.token = token
        self.label = label


@hug.post('/ent')
def ent(text: str, model: str):
    """Get entities for polyglot Entities."""

    emails = re.findall(r'[\w\.-]+@[\w\.-]+', text)

    passport_regex = "[A-PR-WYa-pr-wy][1-9]\\d" + "\\s?\\d{4}[1-9]"
    passport_p = re.compile(passport_regex)
    passports = re.findall(passport_p, text)

    phone_regex = r"\b[0-9]{2,3}-? ?[0-9]{6,7}\b"
    phone_p = re.compile(phone_regex)
    phones = re.findall(phone_p, text)

    dni_regex = r"\b[0-9]{8,8}[A-Za-z]\b"
    dni_p = re.compile(dni_regex)
    dnis = re.findall(dni_p, text)

    nie_regex = r"\b[XYZ][0-9]{7,8}[A-Z]\b"
    nie_p = re.compile(nie_regex)
    nies = re.findall(nie_p, text)

    cif_regex = r"\b[ABCDEFGHJKLMNPQRSUVW][0-9]{7}[0-9A-J]\b"
    cif_p = re.compile(cif_regex)
    cifs = re.findall(cif_p, text)

    polyglot_text = Text(text,  hint_language_code=model)

    tokens = []
    for entity in polyglot_text.entities:

        if entity.tag == 'I-LOC':
            tokens.append(TurPolyToken(' '.join(entity), 'LOC').__dict__)

        if entity.tag == 'I-PER':            
            person = ' '.join(entity).replace(" .", ".").replace("Vocal", "")
            #tokens.append(TurPolyToken(person, 'PN').__dict__)
            humanName = HumanName(person)

            if humanName.first:
                tokens.append(TurPolyToken(humanName.first, 'FIRST_NAME').__dict__)

            if humanName.last:
                tokens.append(TurPolyToken(humanName.surnames, 'LAST_NAME').__dict__)

        if entity.tag == 'I-ORG':
            tokens.append(TurPolyToken(' '.join(entity), 'ON').__dict__)

        if passports:
            for passport in passports:
                tokens.append(TurPolyToken(passport, 'PASSPORT').__dict__)

        if phones:
            for phone in phones:
                tokens.append(TurPolyToken(phone, 'PHONE').__dict__)

        if dnis:
            for dni in dnis:
                tokens.append(TurPolyToken(dni, 'DNI').__dict__)
        if nies:
            for nie in nies:
                tokens.append(TurPolyToken(nie, 'NIE').__dict__)
        if cifs:
            for cif in cifs:
                tokens.append(TurPolyToken(cif, 'CIF').__dict__)

    return tokens


if __name__ == '__main__':
    app = hug.API(__name__)
    app.http.add_middleware(CORSMiddleware(app))
