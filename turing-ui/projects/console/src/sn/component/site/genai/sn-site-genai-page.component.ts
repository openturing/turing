import {Component, OnInit} from '@angular/core';
import {Observable} from 'rxjs';
import {TurSNSite} from "../../../model/sn-site.model";
import {TurSNSiteService} from "../../../service/sn-site.service";
import {ActivatedRoute} from "@angular/router";
import {NotifierService} from "angular-notifier-updated";
import {TurLLMInstance} from "../../../../llm/model/llm-instance.model";
import {TurStoreInstance} from "../../../../store/model/store-instance.model";
import {TurLLMInstanceService} from "../../../../llm/service/llm-instance.service";
import {TurStoreInstanceService} from "../../../../store/service/store-instance.service";
import {TurSNSiteGenAi} from "../../../model/sn-site-genai.model";

@Component({
  selector: 'sn-site--genai-page',
  templateUrl: './sn-site-genai-page.component.html',
  standalone: false
})
export class TurSNSiteGenAiPageComponent implements OnInit {
  private turSNSite: Observable<TurSNSite>;
  private turLLMInstances: Observable<TurLLMInstance[]>;
  private turStoreInstances: Observable<TurStoreInstance[]>;

  constructor(private readonly notifier: NotifierService,
              private turSNSiteService: TurSNSiteService,
              private turLLMInstanceService: TurLLMInstanceService,
              private turStoreInstanceService: TurStoreInstanceService,
              private route: ActivatedRoute) {
    this.turLLMInstances = turLLMInstanceService.query();
    this.turStoreInstances = turStoreInstanceService.query();
    let id = this.route.parent?.snapshot.paramMap.get('id') || "";
    this.turSNSite = this.turSNSiteService.get(id);

  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  ngOnInit(): void {
  }


  public saveSite(_turSNSite: TurSNSite) {
    this.turSNSiteService.save(_turSNSite, false).subscribe(
      (turSNSite: TurSNSite) => {
        _turSNSite = turSNSite;
        this.notifier.notify("success", turSNSite.name.concat(" semantic navigation site was updated."));
      },
      response => {
        this.notifier.notify("error", "SN site was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }

  public compareLLMInstance(n1: TurLLMInstance, n2: TurLLMInstance): boolean {
    return n1 && n2 ? n1.id === n2.id : n1 === n2;
  }

  public compareStoreInstance(n1: TurStoreInstance, n2: TurStoreInstance): boolean {
    return n1 && n2 ? n1.id === n2.id : n1 === n2;
  }

  getTurLLMInstance(): Observable<TurLLMInstance[]> {
    return this.turLLMInstances;
  }

  getTurStoreInstance(): Observable<TurStoreInstance[]> {
    return this.turStoreInstances;
  }

  getDefaults(turSNSiteGenAi: TurSNSiteGenAi) {
    if (turSNSiteGenAi.enabled && turSNSiteGenAi.systemPrompt == null) {
      turSNSiteGenAi.systemPrompt = "Using only this rag data. Answer in Portuguese." +
      "You are a helpful assistant that can answer questions about the web site:\n"
      + "\n"
      + "Question:\n"
      + "{{question}}\n"
      + "\n"
      + "Base your answer on the following information:\n"
      + "{{information}}"
    }
  }
}
